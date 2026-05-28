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
	"github.com/chan808/agolive-realtime/hub"
	"github.com/chan808/agolive-realtime/model"
	"github.com/coder/websocket"
	"github.com/golang-jwt/jwt/v5"
	"github.com/redis/go-redis/v9"
)

const maxAgentsPerRoom = 4

// 에이전트 아바타 위치 (최대 4개, 겹침 방지)
var agentPositions = [][2]float64{
	{900, 200}, {700, 200}, {900, 500}, {700, 500},
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

const initialX = 600.0
const initialY = 400.0

func (h *Handler) enter(ctx context.Context, c *hub.Client) {
	h.rdb.SAdd(ctx, "room:members:"+c.RoomID, c.UserID)
	h.hub.Join(c)

	// 입장 이벤트 브로드캐스트
	h.hub.Publish(ctx, c.RoomID, mustMarshal(model.JoinEvent{Type: "join", UserID: c.UserID, Nickname: c.Nickname}))

	// 신규 입장자 초기 위치를 기존 접속자에게 브로드캐스트
	presenceVal, _ := json.Marshal(map[string]any{"x": initialX, "y": initialY, "nickname": c.Nickname, "avatarId": c.AvatarID})
	h.rdb.Set(ctx, fmt.Sprintf("presence:%s:%d", c.RoomID, c.UserID), presenceVal, 30*time.Second)
	h.hub.Publish(ctx, c.RoomID, mustMarshal(model.PresenceEvent{
		Type: "presence", UserID: c.UserID, X: initialX, Y: initialY, Nickname: c.Nickname, AvatarID: c.AvatarID,
	}))

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
				Nickname string  `json:"nickname"`
				AvatarID *int64  `json:"avatarId"`
			}
			if json.Unmarshal([]byte(val), &p) != nil {
				continue
			}
			sendToClient(c, mustMarshal(model.PresenceEvent{
				Type: "presence", UserID: userID, X: p.X, Y: p.Y, Nickname: p.Nickname, AvatarID: p.AvatarID,
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

	// presence 저장 (TTL 30s)
	presenceVal, _ := json.Marshal(map[string]any{
		"x": *msg.X, "y": *msg.Y, "nickname": c.Nickname, "avatarId": c.AvatarID,
	})
	h.rdb.Set(ctx, fmt.Sprintf("presence:%s:%d", c.RoomID, c.UserID), presenceVal, 30*time.Second)

	broadcast := mustMarshal(model.PresenceEvent{
		Type: "presence", UserID: c.UserID, X: *msg.X, Y: *msg.Y,
		Nickname: c.Nickname, AvatarID: c.AvatarID,
	})
	h.hub.Publish(ctx, c.RoomID, broadcast)
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

	// @mention이 있으면 언급된 에이전트에게만, 없으면 모든 에이전트에게 전달
	agents := h.hub.GetAgents(c.RoomID)
	hasMention := strings.Contains(msg.Content, "@")
	for _, agent := range agents {
		if !hasMention || strings.Contains(msg.Content, "@"+agent.Nickname) {
			go h.streamAgentResponse(agent.AgentID, c.RoomID, c.UserID, c.Nickname, msg.Content)
		}
	}
}

func (h *Handler) handleSummonAgent(ctx context.Context, c *hub.Client, msg model.ClientMessage) {
	if h.hub.AgentCount(c.RoomID) >= maxAgentsPerRoom {
		sendError(c, "AGENT_LIMIT_EXCEEDED", fmt.Sprintf("에이전트는 최대 %d개까지 소환할 수 있습니다.", maxAgentsPerRoom))
		return
	}

	role := msg.Role
	if role == "" {
		role = "helper"
	}

	// 현재 에이전트 수 기준으로 위치 결정
	idx := h.hub.AgentCount(c.RoomID) % len(agentPositions)
	x, y := agentPositions[idx][0], agentPositions[idx][1]

	body, _ := json.Marshal(map[string]any{"roomId": c.RoomID, "role": role, "x": x, "y": y})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions", bytes.NewReader(body))
	if err != nil {
		sendError(c, "INTERNAL_ERROR", "에이전트 소환에 실패했습니다.")
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.hc.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
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
		AgentID  string  `json:"agentId"`
		Nickname string  `json:"nickname"`
		Role     string  `json:"role"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		sendError(c, "INTERNAL_ERROR", "에이전트 응답 파싱 실패.")
		return
	}

	agentCtx, cancelFn := context.WithCancel(context.Background())
	_ = agentCtx
	state := &hub.AgentState{
		AgentID:      result.AgentID,
		Role:         result.Role,
		Nickname:     result.Nickname,
		X:            x,
		Y:            y,
		CancelFn:     cancelFn,
		HumanInputCh: make(chan string, 1),
	}
	h.hub.AddAgent(c.RoomID, state)

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

func (h *Handler) streamAgentResponse(agentID, roomID string, userID int64, nickname, content string) {
	body, _ := json.Marshal(map[string]any{
		"userId":   userID,
		"nickname": nickname,
		"content":  content,
	})
	req, err := http.NewRequest(http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions/"+agentID+"/message",
		bytes.NewReader(body))
	if err != nil {
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.agentC.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		slog.Error("에이전트 메시지 요청 실패", "err", err, "agentId", agentID)
		return
	}
	defer resp.Body.Close()

	h.consumeAgentSSE(resp, agentID, roomID)
}

// consumeAgentSSE는 에이전트 SSE 스트림을 소비하며 이벤트를 처리한다.
// tool_use 이벤트를 만나면 도구를 실행하고 tool_result를 주입한 뒤 스트림을 계속 읽는다.
func (h *Handler) consumeAgentSSE(resp *http.Response, agentID, roomID string) {
	ctx := context.Background()
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
				payload := line[6:]
				var event sseEvent
				if json.Unmarshal([]byte(payload), &event) != nil {
					continue
				}

				switch event.Type {
				case "tool_use":
					h.handleToolUse(ctx, agentID, roomID, event)
				case "file":
					h.hub.Publish(ctx, roomID, mustMarshal(model.AgentFileEvent{
						Type: "agent_file", AgentID: agentID,
						Filename: event.Filename, URL: event.URL, MimeType: event.MimeType,
					}))
				default:
					// type이 "" 또는 "text"인 경우 모두 텍스트로 처리
					h.hub.Publish(ctx, roomID, mustMarshal(model.AgentMessageEvent{
						Type: "agent_message", AgentID: agentID,
						Content: event.Content, Done: event.Done,
					}))
					if event.Done {
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

// handleToolUse는 tool_use 이벤트를 처리한다.
func (h *Handler) handleToolUse(ctx context.Context, agentID, roomID string, event sseEvent) {
	agent := h.hub.GetAgent(roomID, agentID)
	if agent == nil {
		return
	}

	switch event.ToolName {
	case "request_human_input":
		var input struct {
			Prompt  string   `json:"prompt"`
			Options []string `json:"options"`
		}
		json.Unmarshal(event.ToolInput, &input)

		h.hub.Publish(ctx, roomID, mustMarshal(model.AgentNeedsInputEvent{
			Type: "agent_needs_input", AgentID: agentID,
			ToolUseID: event.ToolUseID,
			Prompt:    input.Prompt, Options: input.Options,
		}))

		// 사용자 응답 대기 (최대 120초)
		var userResponse string
		select {
		case userResponse = <-agent.HumanInputCh:
		case <-time.After(120 * time.Second):
			userResponse = "(사용자 응답 없음)"
		}
		h.sendToolResult(ctx, agentID, event.ToolUseID, userResponse)

	case "delegate_to_worker":
		result := h.executeDelegateToWorker(ctx, roomID, agentID, event)
		h.sendToolResult(ctx, agentID, event.ToolUseID, result)

	case "create_document":
		url := h.executeCreateDocument(ctx, agentID, roomID, event)
		h.sendToolResult(ctx, agentID, event.ToolUseID, url)

	default:
		slog.Warn("미지원 tool_use", "toolName", event.ToolName, "agentId", agentID)
		h.sendToolResult(ctx, agentID, event.ToolUseID, "지원하지 않는 도구입니다.")
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

	// 워커 소환 위치 계산
	count := h.hub.AgentCount(roomID)
	idx := count % len(agentPositions)
	x, y := agentPositions[idx][0], agentPositions[idx][1]

	body, _ := json.Marshal(map[string]any{"roomId": roomID, "role": input.Role, "x": x, "y": y})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions", bytes.NewReader(body))
	if err != nil {
		return "워커 소환 실패"
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Secret", h.cfg.InternalSecret)

	resp, err := h.hc.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		if resp != nil {
			resp.Body.Close()
		}
		return "워커 소환 실패"
	}
	defer resp.Body.Close()

	var workerInfo struct {
		AgentID  string `json:"agentId"`
		Nickname string `json:"nickname"`
		Role     string `json:"role"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&workerInfo); err != nil {
		return "워커 응답 파싱 실패"
	}

	// Hub에 워커 등록 + 입장 브로드캐스트
	_, cancelFn := context.WithCancel(context.Background())
	workerState := &hub.AgentState{
		AgentID: workerInfo.AgentID, Role: workerInfo.Role,
		Nickname: workerInfo.Nickname, X: x, Y: y,
		CancelFn:     cancelFn,
		HumanInputCh: make(chan string, 1),
	}
	h.hub.AddAgent(roomID, workerState)
	h.hub.Publish(ctx, roomID, mustMarshal(model.AgentJoinedEvent{
		Type: "agent_joined", AgentID: workerInfo.AgentID,
		Role: workerInfo.Role, Nickname: workerInfo.Nickname, X: x, Y: y,
	}))

	// 태스크 실행 + 응답 수집
	workerResponse := h.streamWorkerAndCollect(workerInfo.AgentID, roomID, input.Task)

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
func (h *Handler) streamWorkerAndCollect(agentID, roomID, task string) string {
	body, _ := json.Marshal(map[string]any{
		"userId": 0, "nickname": "Orchestrator", "content": task,
	})
	req, err := http.NewRequest(http.MethodPost,
		h.cfg.AgentAPIURL+"/internal/agent/sessions/"+agentID+"/message",
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
					h.sendToolResult(ctx, agentID, ev.ToolUseID, "(도구 중첩 미지원)")
				case "file":
					h.hub.Publish(ctx, roomID, mustMarshal(model.AgentFileEvent{
						Type: "agent_file", AgentID: agentID,
						Filename: ev.Filename, URL: ev.URL, MimeType: ev.MimeType,
					}))
				default:
					fullText.WriteString(ev.Content)
					h.hub.Publish(ctx, roomID, mustMarshal(model.AgentMessageEvent{
						Type: "agent_message", AgentID: agentID,
						Content: ev.Content, Done: ev.Done,
					}))
					if ev.Done {
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
func (h *Handler) executeCreateDocument(ctx context.Context, agentID, roomID string, event sseEvent) string {
	resp, err := h.hc.Post(
		h.cfg.AgentAPIURL+"/internal/files",
		"application/json",
		bytes.NewReader(event.ToolInput),
	)
	if err != nil || resp.StatusCode != http.StatusOK {
		if resp != nil {
			resp.Body.Close()
		}
		slog.Error("파일 생성 실패", "agentId", agentID, "err", err)
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
		Type: "agent_file", AgentID: agentID,
		Filename: result.Filename, URL: result.URL, MimeType: result.MimeType,
	}))

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
