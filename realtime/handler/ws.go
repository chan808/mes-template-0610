package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/chan808/agolive-realtime/config"
	"github.com/chan808/agolive-realtime/game"
	"github.com/chan808/agolive-realtime/hub"
	"github.com/chan808/agolive-realtime/model"
	"github.com/coder/websocket"
	"github.com/golang-jwt/jwt/v5"
	"github.com/redis/go-redis/v9"
)

const maxAgentsPerRoom = 4

// HitL 사용자 응답 대기 한도 — Python tool_result 대기(180s)보다 짧아야 응답 유실이 없다
const humanInputTimeout = 120 * time.Second

// 에이전트 아바타 타일 위치 (슬롯 인덱스 기반, 겹침 방지)
var agentPositions = [][2]float64{
	{22, 5}, {17, 5}, {22, 12}, {17, 12},
}

type Handler struct {
	hub    *hub.Hub
	rdb    *redis.Client
	cfg    *config.Config
	hc     *http.Client
	agentC *http.Client // agent SSE용 — timeout 없음
}

func New(h *hub.Hub, rdb *redis.Client, cfg *config.Config) *Handler {
	return &Handler{
		hub:    h,
		rdb:    rdb,
		cfg:    cfg,
		hc:     &http.Client{Timeout: 5 * time.Second},
		agentC: &http.Client{},
	}
}

type userInfo struct {
	UserID   int64
	Nickname string
	AvatarID *int64
}

type roomInfo struct {
	MaxCapacity int
	Status      string
}

type savedMessage struct {
	ID        int64  `json:"id"`
	CreatedAt string `json:"createdAt"`
}

func (h *Handler) ServeWS(w http.ResponseWriter, r *http.Request) {
	roomID := r.PathValue("roomId")
	token := r.URL.Query().Get("token")

	if token == "" {
		writeJSONError(w, "UNAUTHORIZED", "토큰이 필요합니다.", http.StatusUnauthorized)
		return
	}

	// JWT 로컬 파싱 (빠른 실패)
	if err := validateJWT(token, h.cfg.JWTSecret); err != nil {
		writeJSONError(w, "UNAUTHORIZED", "유효하지 않은 토큰입니다.", http.StatusUnauthorized)
		return
	}

	// Spring /internal/auth/verify 호출 (tokenVersion + 유저 정보)
	user, err := h.verifyToken(r.Context(), token)
	if err != nil {
		slog.Warn("토큰 검증 실패", "err", err)
		writeJSONError(w, "UNAUTHORIZED", "인증에 실패했습니다.", http.StatusUnauthorized)
		return
	}

	// Room 정보 조회
	room, err := h.getRoomInfo(r.Context(), roomID)
	if err != nil {
		writeJSONError(w, "ROOM_NOT_FOUND", "존재하지 않는 방입니다.", http.StatusNotFound)
		return
	}
	if room.Status == "closed" {
		writeJSONError(w, "ROOM_CLOSED", "이미 닫힌 방입니다.", http.StatusConflict)
		return
	}

	// 정원 초과 확인
	memberCount, _ := h.rdb.SCard(r.Context(), "room:members:"+roomID).Result()
	if int(memberCount) >= room.MaxCapacity {
		writeJSONError(w, "ROOM_FULL", "정원이 초과되었습니다.", http.StatusConflict)
		return
	}

	// WebSocket 업그레이드
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		OriginPatterns: []string{h.cfg.AllowedOrigin},
	})
	if err != nil {
		return
	}

	c := hub.NewClient(user.UserID, user.Nickname, user.AvatarID, roomID)
	h.serve(conn, c)
}

func (h *Handler) serve(conn *websocket.Conn, c *hub.Client) {
	ctx, cancel := context.WithCancel(context.Background())

	// 쓰기 루프 (별도 고루틴)
	go func() {
		defer conn.CloseNow()
		defer cancel()
		writePump(ctx, conn, c)
	}()

	h.enter(ctx, c)
	defer cancel()
	defer h.leave(c)

	// 읽기 루프
	for {
		_, data, err := conn.Read(ctx)
		if err != nil {
			return
		}
		h.handleMessage(ctx, c, data)
	}
}

func writePump(ctx context.Context, conn *websocket.Conn, c *hub.Client) {
	for {
		select {
		case data := <-c.Send:
			if err := conn.Write(ctx, websocket.MessageText, data); err != nil {
				return
			}
		case <-c.Done():
			return
		case <-ctx.Done():
			return
		}
	}
}

func (h *Handler) enter(ctx context.Context, c *hub.Client) {
	h.rdb.SAdd(ctx, "room:members:"+c.RoomID, c.UserID)
	h.hub.Join(c)

	// 입장 이벤트 브로드캐스트
	h.hub.Publish(ctx, c.RoomID, mustMarshal(model.JoinEvent{Type: "join", UserID: c.UserID, Nickname: c.Nickname}))

	// 신규 입장자 스폰 위치를 저장하고 브로드캐스트 (본인 포함 — 클라이언트는 이를 권위 위치로 채택)
	h.savePresence(ctx, c)
	h.hub.Publish(ctx, c.RoomID, presenceEventOf(c))

	// 기존 접속자 위치를 신규 입장자에게 전송
	h.sendExistingPresences(ctx, c)

	// 현재 활성 에이전트 목록을 신규 입장자에게 전송
	for _, a := range h.hub.GetAgents(c.RoomID) {
		sendToClient(c, mustMarshal(model.AgentJoinedEvent{
			Type: "agent_joined", AgentID: a.AgentID, Role: a.Role,
			Nickname: a.Nickname, X: a.X, Y: a.Y,
		}))
	}

	slog.Info("유저 입장", "userId", c.UserID, "roomId", c.RoomID)
}

func (h *Handler) sendExistingPresences(ctx context.Context, c *hub.Client) {
	pattern := fmt.Sprintf("presence:%s:*", c.RoomID)
	var cursor uint64
	for {
		keys, next, err := h.rdb.Scan(ctx, cursor, pattern, 100).Result()
		if err != nil {
			return
		}
		for _, key := range keys {
			parts := strings.Split(key, ":")
			userID, err := strconv.ParseInt(parts[len(parts)-1], 10, 64)
			if err != nil || userID == c.UserID {
				continue
			}
			val, err := h.rdb.Get(ctx, key).Result()
			if err != nil {
				continue
			}
			var p struct {
				X        float64 `json:"x"`
				Y        float64 `json:"y"`
				Dir      string  `json:"dir"`
				Nickname string  `json:"nickname"`
				AvatarID *int64  `json:"avatarId"`
			}
			if json.Unmarshal([]byte(val), &p) != nil {
				continue
			}
			sendToClient(c, mustMarshal(model.PresenceEvent{
				Type: "presence", UserID: userID, X: p.X, Y: p.Y, Dir: p.Dir, Nickname: p.Nickname, AvatarID: p.AvatarID,
			}))
		}
		if next == 0 {
			return
		}
		cursor = next
	}
}

func (h *Handler) leave(c *hub.Client) {
	ctx := context.Background()

	// leave 이벤트를 hub에서 제거하기 전에 발행
	msg := mustMarshal(model.LeaveEvent{Type: "leave", UserID: c.UserID})
	h.hub.Publish(ctx, c.RoomID, msg)

	// 마지막 클라이언트 퇴장으로 룸이 비면 고아 에이전트 전부 정리
	orphaned := h.hub.Leave(c)
	for _, a := range orphaned {
		a.CancelFn()
		go h.cleanupAgentSession(a.AgentID)
	}

	h.rdb.SRem(ctx, "room:members:"+c.RoomID, c.UserID)
	h.rdb.Del(ctx, fmt.Sprintf("presence:%s:%d", c.RoomID, c.UserID))
	slog.Info("유저 퇴장", "userId", c.UserID, "roomId", c.RoomID)
}

func (h *Handler) cleanupAgentSession(agentID string) {
	req, err := http.NewRequest(http.MethodDelete,
		h.cfg.AgentAPIURL+"/internal/agent/sessions/"+agentID, nil)
	if err != nil {
		return
	}
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)
	resp, _ := h.hc.Do(req)
	if resp != nil {
		resp.Body.Close()
	}
	slog.Info("고아 에이전트 정리", "agentId", agentID)
}

func (h *Handler) handleMessage(ctx context.Context, c *hub.Client, data []byte) {
	var msg model.ClientMessage
	if err := json.Unmarshal(data, &msg); err != nil {
		sendError(c, "INVALID_MESSAGE", "잘못된 메시지 형식입니다.")
		return
	}

	switch msg.Type {
	case "move":
		h.handleMove(ctx, c, msg)
	case "chat":
		h.handleChat(ctx, c, msg)
	case "ping":
		h.handlePing(ctx, c)
	case "summon_agent":
		h.handleSummonAgent(ctx, c, msg)
	case "dismiss_agent":
		h.handleDismissAgent(ctx, c, msg)
	case "agent_input":
		h.handleAgentInput(c, msg)
	default:
		sendError(c, "UNKNOWN_EVENT", "알 수 없는 이벤트입니다.")
	}
}

func (h *Handler) handleMove(ctx context.Context, c *hub.Client, msg model.ClientMessage) {
	if msg.X == nil || msg.Y == nil {
		sendError(c, "INVALID_PAYLOAD", "잘못된 move 페이로드입니다.")
		return
	}
	toX, okX := game.ParseTileCoord(*msg.X)
	toY, okY := game.ParseTileCoord(*msg.Y)
	if !okX || !okY || !game.IsValidDir(msg.Dir) {
		sendError(c, "INVALID_PAYLOAD", "잘못된 move 페이로드입니다.")
		return
	}

	// 인접성·범위·속도 검증 실패 시 서버 권위 위치를 회신해 클라이언트가 스스로 보정하게 한다
	if !game.CanMove(c.TileX, c.TileY, toX, toY) || !c.MoveLimiter.Allow(time.Now()) {
		sendToClient(c, presenceEventOf(c))
		return
	}

	c.TileX, c.TileY, c.Dir = toX, toY, msg.Dir
	h.savePresence(ctx, c)
	h.hub.Publish(ctx, c.RoomID, presenceEventOf(c))
}

// savePresence는 클라이언트의 서버 권위 위치를 Redis에 저장한다 (TTL 30s)
func (h *Handler) savePresence(ctx context.Context, c *hub.Client) {
	presenceVal, _ := json.Marshal(map[string]any{
		"x": c.TileX, "y": c.TileY, "dir": c.Dir, "nickname": c.Nickname, "avatarId": c.AvatarID,
	})
	h.rdb.Set(ctx, fmt.Sprintf("presence:%s:%d", c.RoomID, c.UserID), presenceVal, 30*time.Second)
}

// presenceEventOf는 클라이언트의 서버 권위 위치로 presence 이벤트를 만든다
func presenceEventOf(c *hub.Client) []byte {
	return mustMarshal(model.PresenceEvent{
		Type: "presence", UserID: c.UserID, X: float64(c.TileX), Y: float64(c.TileY),
		Dir: c.Dir, Nickname: c.Nickname, AvatarID: c.AvatarID,
	})
}

func (h *Handler) handleChat(ctx context.Context, c *hub.Client, msg model.ClientMessage) {
	if msg.Content == "" {
		sendError(c, "INVALID_PAYLOAD", "잘못된 chat 페이로드입니다.")
		return
	}

	record, err := h.saveMessage(ctx, c, msg.Content)
	if err != nil {
		slog.Error("메시지 저장 실패", "err", err, "userId", c.UserID, "roomId", c.RoomID)
		sendError(c, "INTERNAL_ERROR", "메시지 저장에 실패했습니다.")
		return
	}

	broadcast := mustMarshal(model.ChatEvent{
		Type: "chat", MessageID: record.ID, UserID: c.UserID,
		Content: msg.Content, CreatedAt: record.CreatedAt,
	})
	h.hub.Publish(ctx, c.RoomID, broadcast)

	// @닉네임 멘션이 있으면 해당 에이전트에게만, 없으면 모든 에이전트에게 전달
	for _, agent := range selectAgentTargets(msg.Content, h.hub.GetAgents(c.RoomID)) {
		go h.streamAgentResponse(agent, c.RoomID, c.UserID, c.Nickname, msg.Content)
	}
}

// selectAgentTargets는 메시지를 전달할 에이전트를 고른다.
// 등록된 에이전트 닉네임과 일치하는 @멘션이 있으면 해당 에이전트만,
// 없으면(이메일 등 무관한 @ 포함) 전체 에이전트를 반환한다.
// 각 '@' 위치에서 가장 긴 닉네임 하나만 인정한다 ("@AI 도우미 2"가 "AI 도우미"에 중복 매칭되지 않게).
func selectAgentTargets(content string, agents []*hub.AgentState) []*hub.AgentState {
	mentionedIDs := make(map[string]bool)
	for i := 0; i < len(content); i++ {
		if content[i] != '@' {
			continue
		}
		rest := content[i+1:]
		var best *hub.AgentState
		for _, a := range agents {
			if strings.HasPrefix(rest, a.Nickname) {
				if best == nil || len(a.Nickname) > len(best.Nickname) {
					best = a
				}
			}
		}
		if best != nil {
			mentionedIDs[best.AgentID] = true
		}
	}
	if len(mentionedIDs) == 0 {
		return agents
	}
	var mentioned []*hub.AgentState
	for _, a := range agents {
		if mentionedIDs[a.AgentID] {
			mentioned = append(mentioned, a)
		}
	}
	return mentioned
}

func (h *Handler) handleSummonAgent(ctx context.Context, c *hub.Client, msg model.ClientMessage) {
	// 슬롯 예약으로 정원 확인과 위치 결정을 원자적으로 처리 (동시 소환 race 방지)
	slot, ok := h.hub.TryReserveAgentSlot(c.RoomID, maxAgentsPerRoom)
	if !ok {
		sendError(c, "AGENT_LIMIT_EXCEEDED", fmt.Sprintf("에이전트는 최대 %d개까지 소환할 수 있습니다.", maxAgentsPerRoom))
		return
	}

	role := msg.Role
	if role == "" {
		role = "helper"
	}
	x, y := agentPositions[slot][0], agentPositions[slot][1]

	body, _ := json.Marshal(map[string]any{"roomId": c.RoomID, "role": role, "x": x, "y": y})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions", bytes.NewReader(body))
	if err != nil {
		h.hub.ReleaseAgentSlot(c.RoomID, slot)
		sendError(c, "INTERNAL_ERROR", "에이전트 소환에 실패했습니다.")
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.hc.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		h.hub.ReleaseAgentSlot(c.RoomID, slot)
		if resp != nil && resp.StatusCode == http.StatusConflict {
			sendError(c, "AGENT_LIMIT_EXCEEDED", "에이전트 최대 수에 도달했습니다.")
		} else {
			slog.Error("에이전트 소환 실패", "err", err, "roomId", c.RoomID)
			sendError(c, "INTERNAL_ERROR", "에이전트 소환에 실패했습니다.")
		}
		if resp != nil {
			resp.Body.Close()
		}
		return
	}
	defer resp.Body.Close()

	var result struct {
		AgentID  string `json:"agentId"`
		Nickname string `json:"nickname"`
		Role     string `json:"role"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		h.hub.ReleaseAgentSlot(c.RoomID, slot)
		sendError(c, "INTERNAL_ERROR", "에이전트 응답 파싱 실패.")
		return
	}

	// 에이전트 수명주기 컨텍스트 — dismiss/방 정리 시 진행 중인 스트림과 HitL 대기를 취소한다
	agentCtx, cancelFn := context.WithCancel(context.Background())
	state := &hub.AgentState{
		AgentID:      result.AgentID,
		Role:         result.Role,
		Nickname:     result.Nickname,
		X:            x,
		Y:            y,
		Slot:         slot,
		Ctx:          agentCtx,
		CancelFn:     cancelFn,
		HumanInputCh: make(chan string, 1),
	}
	// 소환 도중 방이 정리됐으면 Python 세션도 함께 정리 (고아 세션 방지)
	if !h.hub.CommitAgent(c.RoomID, state) {
		cancelFn()
		go h.cleanupAgentSession(result.AgentID)
		return
	}

	h.hub.Publish(ctx, c.RoomID, mustMarshal(model.AgentJoinedEvent{
		Type: "agent_joined", AgentID: result.AgentID, Role: result.Role,
		Nickname: result.Nickname, X: x, Y: y,
	}))
	slog.Info("에이전트 소환", "agentId", result.AgentID, "roomId", c.RoomID, "role", result.Role)
}

func (h *Handler) handleDismissAgent(ctx context.Context, c *hub.Client, msg model.ClientMessage) {
	if msg.AgentID == "" {
		sendError(c, "INVALID_PAYLOAD", "agentId가 필요합니다.")
		return
	}

	agent := h.hub.RemoveAgent(c.RoomID, msg.AgentID)
	if agent == nil {
		sendError(c, "NO_AGENT", "해당 에이전트가 없습니다.")
		return
	}

	agent.CancelFn()

	req, err := http.NewRequestWithContext(ctx, http.MethodDelete,
		h.cfg.AgentAPIURL+"/internal/agent/sessions/"+agent.AgentID, nil)
	if err == nil {
		req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)
		resp, _ := h.hc.Do(req)
		if resp != nil {
			resp.Body.Close()
		}
	}

	h.hub.Publish(ctx, c.RoomID, mustMarshal(model.AgentLeftEvent{
		Type: "agent_left", AgentID: agent.AgentID,
	}))
	slog.Info("에이전트 퇴장", "agentId", agent.AgentID, "roomId", c.RoomID)
}

// handleAgentInput은 HitL 응답을 대기 중인 streamAgentResponse 고루틴에 전달한다
func (h *Handler) handleAgentInput(c *hub.Client, msg model.ClientMessage) {
	if msg.AgentID == "" || msg.Response == "" {
		sendError(c, "INVALID_PAYLOAD", "agentId와 response가 필요합니다.")
		return
	}

	agent := h.hub.GetAgent(c.RoomID, msg.AgentID)
	if agent == nil {
		sendError(c, "NO_AGENT", "해당 에이전트가 없습니다.")
		return
	}

	// 비블로킹: 이미 응답이 있거나 대기 중이 아니면 무시
	select {
	case agent.HumanInputCh <- msg.Response:
	default:
	}
}

// sseEvent는 agolive-agent SSE 이벤트의 공통 구조다
type sseEvent struct {
	Type      string          `json:"type"`       // "", "text", "tool_use", "file"
	Content   string          `json:"content"`
	Done      bool            `json:"done"`
	Error     string          `json:"error"`
	ToolName  string          `json:"toolName"`
	ToolInput json.RawMessage `json:"toolInput"`
	ToolUseID string          `json:"toolUseId"`
	Prompt    string          `json:"prompt"`
	Options   []string        `json:"options"`
	Filename  string          `json:"filename"`
	URL       string          `json:"url"`
	MimeType  string          `json:"mimeType"`
}

func (h *Handler) streamAgentResponse(agent *hub.AgentState, roomID string, userID int64, nickname, content string) {
	body, _ := json.Marshal(map[string]any{
		"userId":   userID,
		"nickname": nickname,
		"content":  content,
	})
	// 에이전트 수명주기 컨텍스트 사용 — dismiss 시 진행 중인 SSE 요청도 함께 취소된다
	req, err := http.NewRequestWithContext(agent.Ctx, http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions/"+agent.AgentID+"/message",
		bytes.NewReader(body))
	if err != nil {
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.agentC.Do(req)
	if err != nil {
		slog.Error("에이전트 메시지 요청 실패", "err", err, "agentId", agent.AgentID)
		return
	}
	defer resp.Body.Close()

	// 세션 소실(에이전트 서비스 재시작 등): hub에서 정리하고 퇴장 브로드캐스트 (좀비 방지)
	if resp.StatusCode == http.StatusNotFound {
		h.removeZombieAgent(roomID, agent)
		return
	}
	if resp.StatusCode != http.StatusOK {
		slog.Error("에이전트 메시지 요청 실패", "status", resp.StatusCode, "agentId", agent.AgentID)
		return
	}

	h.consumeAgentSSE(resp, agent, roomID)
}

// removeZombieAgent는 세션이 사라진 에이전트를 hub에서 제거하고 퇴장을 알린다
func (h *Handler) removeZombieAgent(roomID string, agent *hub.AgentState) {
	if h.hub.RemoveAgent(roomID, agent.AgentID) == nil {
		return
	}
	agent.CancelFn()
	h.hub.Publish(context.Background(), roomID, mustMarshal(model.AgentLeftEvent{
		Type: "agent_left", AgentID: agent.AgentID,
	}))
	slog.Warn("에이전트 세션 소실로 정리", "agentId", agent.AgentID, "roomId", roomID)
}

// consumeAgentSSE는 에이전트 SSE 스트림을 소비하며 이벤트를 처리한다.
// tool_use 이벤트를 만나면 도구를 실행하고 tool_result를 주입한 뒤 스트림을 계속 읽는다.
func (h *Handler) consumeAgentSSE(resp *http.Response, agent *hub.AgentState, roomID string) {
	ctx := context.Background()
	buf := make([]byte, 4096)
	leftover := ""
	var fullText strings.Builder

	for {
		n, err := resp.Body.Read(buf)
		if n > 0 {
			chunk := leftover + string(buf[:n])
			leftover = ""
			lines := strings.Split(chunk, "\n")
			for i, line := range lines {
				if i == len(lines)-1 {
					leftover = line
					continue
				}
				line = strings.TrimSpace(line)
				if !strings.HasPrefix(line, "data: ") {
					continue
				}
				payload := line[6:]
				var event sseEvent
				if json.Unmarshal([]byte(payload), &event) != nil {
					continue
				}

				switch event.Type {
				case "tool_use":
					h.handleToolUse(ctx, agent, roomID, event)
				case "file":
					h.hub.Publish(ctx, roomID, mustMarshal(model.AgentFileEvent{
						Type: "agent_file", AgentID: agent.AgentID,
						Filename: event.Filename, URL: event.URL, MimeType: event.MimeType,
					}))
				default:
					// 오류는 침묵하지 않고 사용자에게 노출한다
					if event.Error != "" {
						slog.Error("에이전트 응답 오류", "err", event.Error, "agentId", agent.AgentID)
						h.hub.Publish(ctx, roomID, mustMarshal(model.AgentMessageEvent{
							Type: "agent_message", AgentID: agent.AgentID,
							Content: "(응답 생성 중 오류가 발생했습니다)", Done: true,
						}))
						return
					}
					// type이 "" 또는 "text"인 경우 모두 텍스트로 처리
					fullText.WriteString(event.Content)
					h.hub.Publish(ctx, roomID, mustMarshal(model.AgentMessageEvent{
						Type: "agent_message", AgentID: agent.AgentID,
						Content: event.Content, Done: event.Done,
					}))
					if event.Done {
						// 응답 전문을 DB에 저장 (새로고침/재입장 시 히스토리 유지)
						h.saveAgentMessage(roomID, agent.Nickname, fullText.String())
						return
					}
				}
			}
		}
		if err != nil {
			return
		}
	}
}

// saveAgentMessage는 에이전트 응답 전문을 type=agent 메시지로 저장한다
func (h *Handler) saveAgentMessage(roomID, nickname, content string) {
	if strings.TrimSpace(content) == "" {
		return
	}
	// 저장 API 길이 제한에 맞게 절단
	runes := []rune(content)
	if len(runes) > 4000 {
		content = string(runes[:4000])
	}

	saveCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	body, _ := json.Marshal(map[string]any{
		"userId": nil, "content": content, "type": "agent", "agentNickname": nickname,
	})
	url := fmt.Sprintf("%s/internal/rooms/%s/messages", h.cfg.InternalAPIURL, roomID)
	req, err := http.NewRequestWithContext(saveCtx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.hc.Do(req)
	if err != nil {
		slog.Error("에이전트 메시지 저장 실패", "err", err, "roomId", roomID)
		return
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusCreated {
		slog.Error("에이전트 메시지 저장 실패", "status", resp.StatusCode, "roomId", roomID)
	}
}

// handleToolUse는 tool_use 이벤트를 처리한다.
func (h *Handler) handleToolUse(ctx context.Context, agent *hub.AgentState, roomID string, event sseEvent) {
	switch event.ToolName {
	case "request_human_input":
		var input struct {
			Prompt  string   `json:"prompt"`
			Options []string `json:"options"`
		}
		json.Unmarshal(event.ToolInput, &input)

		h.hub.Publish(ctx, roomID, mustMarshal(model.AgentNeedsInputEvent{
			Type: "agent_needs_input", AgentID: agent.AgentID,
			ToolUseID: event.ToolUseID,
			Prompt:    input.Prompt, Options: input.Options,
		}))

		// 사용자 응답 대기 — 에이전트 퇴장 시 즉시 중단
		var userResponse string
		select {
		case userResponse = <-agent.HumanInputCh:
		case <-agent.Ctx.Done():
			return
		case <-time.After(humanInputTimeout):
			userResponse = "(사용자 응답 없음)"
		}
		h.sendToolResult(ctx, agent.AgentID, event.ToolUseID, userResponse)

	case "delegate_to_worker":
		result := h.executeDelegateToWorker(ctx, roomID, agent.AgentID, event)
		h.sendToolResult(ctx, agent.AgentID, event.ToolUseID, result)

	case "create_document":
		url := h.executeCreateDocument(ctx, agent, roomID, event)
		h.sendToolResult(ctx, agent.AgentID, event.ToolUseID, url)

	default:
		slog.Warn("미지원 tool_use", "toolName", event.ToolName, "agentId", agent.AgentID)
		h.sendToolResult(ctx, agent.AgentID, event.ToolUseID, "지원하지 않는 도구입니다.")
	}
}

// executeDelegateToWorker는 워커 에이전트를 소환하고 태스크를 실행한 뒤 결과를 반환한다.
func (h *Handler) executeDelegateToWorker(ctx context.Context, roomID, orchestratorID string, event sseEvent) string {
	var input struct {
		Role string `json:"role"`
		Task string `json:"task"`
	}
	if err := json.Unmarshal(event.ToolInput, &input); err != nil || input.Role == "" {
		return "워커 입력 파싱 실패"
	}

	// 진행 상황 브로드캐스트
	orchestrator := h.hub.GetAgent(roomID, orchestratorID)
	if orchestrator != nil {
		h.hub.Publish(ctx, roomID, mustMarshal(model.AgentThinkingEvent{
			Type:    "agent_thinking",
			AgentID: orchestratorID,
			Step:    input.Role + "에게 작업 위임 중...",
		}))
	}

	// 슬롯 예약으로 워커 위치 결정 (소환 race·위치 중복 방지)
	slot, ok := h.hub.TryReserveAgentSlot(roomID, maxAgentsPerRoom)
	if !ok {
		return "워커 소환 실패: 에이전트 정원 초과"
	}
	x, y := agentPositions[slot][0], agentPositions[slot][1]

	body, _ := json.Marshal(map[string]any{"roomId": roomID, "role": input.Role, "x": x, "y": y})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions", bytes.NewReader(body))
	if err != nil {
		h.hub.ReleaseAgentSlot(roomID, slot)
		return "워커 소환 실패"
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.hc.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		if resp != nil {
			resp.Body.Close()
		}
		h.hub.ReleaseAgentSlot(roomID, slot)
		return "워커 소환 실패"
	}
	defer resp.Body.Close()

	var workerInfo struct {
		AgentID  string `json:"agentId"`
		Nickname string `json:"nickname"`
		Role     string `json:"role"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&workerInfo); err != nil {
		h.hub.ReleaseAgentSlot(roomID, slot)
		return "워커 응답 파싱 실패"
	}

	// Hub에 워커 등록 + 입장 브로드캐스트
	workerCtx, cancelFn := context.WithCancel(context.Background())
	workerState := &hub.AgentState{
		AgentID: workerInfo.AgentID, Role: workerInfo.Role,
		Nickname: workerInfo.Nickname, X: x, Y: y,
		Slot:         slot,
		Ctx:          workerCtx,
		CancelFn:     cancelFn,
		HumanInputCh: make(chan string, 1),
	}
	// 위임 도중 방이 정리됐으면 워커 세션도 함께 정리
	if !h.hub.CommitAgent(roomID, workerState) {
		cancelFn()
		go h.cleanupAgentSession(workerInfo.AgentID)
		return "워커 소환 실패: 방이 종료되었습니다"
	}
	h.hub.Publish(ctx, roomID, mustMarshal(model.AgentJoinedEvent{
		Type: "agent_joined", AgentID: workerInfo.AgentID,
		Role: workerInfo.Role, Nickname: workerInfo.Nickname, X: x, Y: y,
	}))

	// 태스크 실행 + 응답 수집
	workerResponse := h.streamWorkerAndCollect(workerState, roomID, input.Task)

	// 워커 정리
	cancelFn()
	go h.cleanupAgentSession(workerInfo.AgentID)
	h.hub.RemoveAgent(roomID, workerInfo.AgentID)
	h.hub.Publish(ctx, roomID, mustMarshal(model.AgentLeftEvent{
		Type: "agent_left", AgentID: workerInfo.AgentID,
	}))

	return workerResponse
}

// streamWorkerAndCollect는 워커에게 태스크를 전달하고 응답을 브로드캐스트하면서 전문 텍스트를 반환한다.
func (h *Handler) streamWorkerAndCollect(worker *hub.AgentState, roomID, task string) string {
	body, _ := json.Marshal(map[string]any{
		"userId": 0, "nickname": "Orchestrator", "content": task,
	})
	req, err := http.NewRequest(http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions/"+worker.AgentID+"/message",
		bytes.NewReader(body))
	if err != nil {
		return ""
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.agentC.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		return ""
	}
	defer resp.Body.Close()

	ctx := context.Background()
	var fullText strings.Builder
	buf := make([]byte, 4096)
	leftover := ""

	for {
		n, err := resp.Body.Read(buf)
		if n > 0 {
			chunk := leftover + string(buf[:n])
			leftover = ""
			lines := strings.Split(chunk, "\n")
			for i, line := range lines {
				if i == len(lines)-1 {
					leftover = line
					continue
				}
				line = strings.TrimSpace(line)
				if !strings.HasPrefix(line, "data: ") {
					continue
				}
				var ev sseEvent
				if json.Unmarshal([]byte(line[6:]), &ev) != nil {
					continue
				}
				switch ev.Type {
				case "tool_use":
					// 워커의 tool_use는 미지원 응답으로 처리 (중첩 방지)
					h.sendToolResult(ctx, worker.AgentID, ev.ToolUseID, "(도구 중첩 미지원)")
				case "file":
					h.hub.Publish(ctx, roomID, mustMarshal(model.AgentFileEvent{
						Type: "agent_file", AgentID: worker.AgentID,
						Filename: ev.Filename, URL: ev.URL, MimeType: ev.MimeType,
					}))
				default:
					fullText.WriteString(ev.Content)
					h.hub.Publish(ctx, roomID, mustMarshal(model.AgentMessageEvent{
						Type: "agent_message", AgentID: worker.AgentID,
						Content: ev.Content, Done: ev.Done,
					}))
					if ev.Done {
						// 워커 응답도 히스토리에 남도록 저장
						h.saveAgentMessage(roomID, worker.Nickname, fullText.String())
						return fullText.String()
					}
				}
			}
		}
		if err != nil {
			return fullText.String()
		}
	}
}

// executeCreateDocument는 Python /internal/files 엔드포인트를 통해 파일을 S3에 업로드하고 URL을 반환한다.
func (h *Handler) executeCreateDocument(ctx context.Context, agent *hub.AgentState, roomID string, event sseEvent) string {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/files",
		bytes.NewReader(event.ToolInput))
	if err != nil {
		slog.Error("파일 생성 요청 오류", "agentId", agent.AgentID, "err", err)
		return "파일 생성에 실패했습니다."
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)
	resp, err := h.hc.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		if resp != nil {
			resp.Body.Close()
		}
		slog.Error("파일 생성 실패", "agentId", agent.AgentID, "err", err)
		return "파일 생성에 실패했습니다."
	}
	defer resp.Body.Close()

	var result struct {
		URL      string `json:"url"`
		Filename string `json:"filename"`
		MimeType string `json:"mimeType"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "파일 응답 파싱 실패"
	}

	// 파일 이벤트 브로드캐스트 (chatStore에서 파일 카드로 렌더링)
	h.hub.Publish(ctx, roomID, mustMarshal(model.AgentFileEvent{
		Type: "agent_file", AgentID: agent.AgentID,
		Filename: result.Filename, URL: result.URL, MimeType: result.MimeType,
	}))

	// 다운로드 링크를 히스토리에도 남긴다 (presigned URL 7일 유효)
	h.saveAgentMessage(roomID, agent.Nickname, fmt.Sprintf("📄 %s\n%s", result.Filename, result.URL))

	return fmt.Sprintf("파일이 생성되었습니다: %s (%s)", result.Filename, result.URL)
}

// sendToolResult는 /internal/agent/sessions/{id}/tool_result로 결과를 주입한다
func (h *Handler) sendToolResult(ctx context.Context, agentID, toolUseID, result string) {
	body, _ := json.Marshal(map[string]any{
		"results": []map[string]any{
			{"type": "tool_result", "tool_use_id": toolUseID, "content": result},
		},
	})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions/"+agentID+"/tool_result",
		bytes.NewReader(body))
	if err != nil {
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)
	resp, _ := h.hc.Do(req)
	if resp != nil {
		resp.Body.Close()
	}
}

func (h *Handler) handlePing(ctx context.Context, c *hub.Client) {
	// presence TTL 갱신
	h.rdb.Expire(ctx, fmt.Sprintf("presence:%s:%d", c.RoomID, c.UserID), 30*time.Second)
	sendToClient(c, mustMarshal(model.PongEvent{Type: "pong"}))
}

// verifyToken은 Spring /internal/auth/verify를 호출해 토큰을 검증하고 유저 정보를 반환한다
func (h *Handler) verifyToken(ctx context.Context, token string) (*userInfo, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, h.cfg.InternalAPIURL+"/internal/auth/verify", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := h.hc.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("verify returned %d", resp.StatusCode)
	}

	var body struct {
		Data struct {
			UserID   int64   `json:"userId"`
			Nickname *string `json:"nickname"`
			AvatarID *int64  `json:"avatarId"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, err
	}

	nickname := fmt.Sprintf("사용자%d", body.Data.UserID)
	if body.Data.Nickname != nil && *body.Data.Nickname != "" {
		nickname = *body.Data.Nickname
	}
	return &userInfo{UserID: body.Data.UserID, Nickname: nickname, AvatarID: body.Data.AvatarID}, nil
}

// getRoomInfo는 Spring /internal/rooms/{roomId}를 호출해 방 정보를 반환한다
func (h *Handler) getRoomInfo(ctx context.Context, roomID string) (*roomInfo, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, h.cfg.InternalAPIURL+"/internal/rooms/"+roomID, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.hc.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("room info returned %d", resp.StatusCode)
	}

	var body struct {
		Data struct {
			MaxCapacity int    `json:"maxCapacity"`
			Status      string `json:"status"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, err
	}
	return &roomInfo{MaxCapacity: body.Data.MaxCapacity, Status: body.Data.Status}, nil
}

// saveMessage는 Spring /internal/rooms/{roomId}/messages에 메시지를 저장한다.
// 커넥션 ctx와 독립된 context를 사용해 연결 종료 시에도 저장을 완료한다.
func (h *Handler) saveMessage(_ context.Context, c *hub.Client, content string) (*savedMessage, error) {
	saveCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	body, _ := json.Marshal(map[string]any{
		"userId": c.UserID, "content": content, "type": "chat",
	})

	url := fmt.Sprintf("%s/internal/rooms/%s/messages", h.cfg.InternalAPIURL, c.RoomID)
	req, err := http.NewRequestWithContext(saveCtx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.hc.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusCreated {
		return nil, fmt.Errorf("save message returned %d", resp.StatusCode)
	}

	var result struct {
		Data savedMessage `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}
	return &result.Data, nil
}

func validateJWT(tokenStr, secret string) error {
	_, err := jwt.Parse(tokenStr, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return []byte(secret), nil
	})
	return err
}

func writeJSONError(w http.ResponseWriter, code, message string, status int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"code": code, "message": message})
}

func sendToClient(c *hub.Client, data []byte) {
	select {
	case c.Send <- data:
	default:
	}
}

func sendError(c *hub.Client, code, message string) {
	sendToClient(c, mustMarshal(model.ErrorEvent{Type: "error", Code: code, Message: message}))
}

func mustMarshal(v any) []byte {
	data, _ := json.Marshal(v)
	return data
}
