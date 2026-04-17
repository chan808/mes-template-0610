package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/chan808/agolive-realtime/config"
	"github.com/chan808/agolive-realtime/hub"
	"github.com/chan808/agolive-realtime/model"
	"github.com/coder/websocket"
	"github.com/golang-jwt/jwt/v5"
	"github.com/redis/go-redis/v9"
)

type Handler struct {
	hub *hub.Hub
	rdb *redis.Client
	cfg *config.Config
	hc  *http.Client
}

func New(h *hub.Hub, rdb *redis.Client, cfg *config.Config) *Handler {
	return &Handler{
		hub: h,
		rdb: rdb,
		cfg: cfg,
		hc:  &http.Client{Timeout: 5 * time.Second},
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
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{InsecureSkipVerify: true})
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

	msg := mustMarshal(model.ServerMessage{
		Type:    "join",
		Payload: model.JoinPayload{UserID: c.UserID, Nickname: c.Nickname},
	})
	h.hub.Publish(ctx, c.RoomID, msg)
	slog.Info("유저 입장", "userId", c.UserID, "roomId", c.RoomID)
}

func (h *Handler) leave(c *hub.Client) {
	ctx := context.Background()

	// leave 이벤트를 hub에서 제거하기 전에 발행
	msg := mustMarshal(model.ServerMessage{
		Type:    "leave",
		Payload: model.LeavePayload{UserID: c.UserID},
	})
	h.hub.Publish(ctx, c.RoomID, msg)

	h.hub.Leave(c)
	h.rdb.SRem(ctx, "room:members:"+c.RoomID, c.UserID)
	h.rdb.Del(ctx, fmt.Sprintf("presence:%s:%d", c.RoomID, c.UserID))
	slog.Info("유저 퇴장", "userId", c.UserID, "roomId", c.RoomID)
}

func (h *Handler) handleMessage(ctx context.Context, c *hub.Client, data []byte) {
	var msg model.ClientMessage
	if err := json.Unmarshal(data, &msg); err != nil {
		sendToClient(c, model.ServerMessage{
			Type:    "error",
			Payload: model.ErrorPayload{Code: "INVALID_MESSAGE", Message: "잘못된 메시지 형식입니다."},
		})
		return
	}

	switch msg.Type {
	case "move":
		h.handleMove(ctx, c, msg.Payload)
	case "chat":
		h.handleChat(ctx, c, msg.Payload)
	case "ping":
		h.handlePing(ctx, c)
	default:
		sendToClient(c, model.ServerMessage{
			Type:    "error",
			Payload: model.ErrorPayload{Code: "UNKNOWN_EVENT", Message: "알 수 없는 이벤트입니다."},
		})
	}
}

func (h *Handler) handleMove(ctx context.Context, c *hub.Client, payload json.RawMessage) {
	var p model.MovePayload
	if err := json.Unmarshal(payload, &p); err != nil {
		sendToClient(c, model.ServerMessage{
			Type:    "error",
			Payload: model.ErrorPayload{Code: "INVALID_PAYLOAD", Message: "잘못된 move 페이로드입니다."},
		})
		return
	}

	// presence 저장 (TTL 30s)
	presenceVal, _ := json.Marshal(map[string]any{
		"x": p.X, "y": p.Y, "nickname": c.Nickname, "avatarId": c.AvatarID,
	})
	h.rdb.Set(ctx, fmt.Sprintf("presence:%s:%d", c.RoomID, c.UserID), presenceVal, 30*time.Second)

	msg := mustMarshal(model.ServerMessage{
		Type: "presence",
		Payload: model.PresencePayload{
			UserID: c.UserID, X: p.X, Y: p.Y,
			Nickname: c.Nickname, AvatarID: c.AvatarID,
		},
	})
	h.hub.Publish(ctx, c.RoomID, msg)
}

func (h *Handler) handleChat(ctx context.Context, c *hub.Client, payload json.RawMessage) {
	var p model.ChatPayload
	if err := json.Unmarshal(payload, &p); err != nil {
		sendToClient(c, model.ServerMessage{
			Type:    "error",
			Payload: model.ErrorPayload{Code: "INVALID_PAYLOAD", Message: "잘못된 chat 페이로드입니다."},
		})
		return
	}

	record, err := h.saveMessage(ctx, c, p.Content)
	if err != nil {
		slog.Error("메시지 저장 실패", "err", err, "userId", c.UserID, "roomId", c.RoomID)
		sendToClient(c, model.ServerMessage{
			Type:    "error",
			Payload: model.ErrorPayload{Code: "INTERNAL_ERROR", Message: "메시지 저장에 실패했습니다."},
		})
		return
	}

	msg := mustMarshal(model.ServerMessage{
		Type: "chat",
		Payload: model.ChatEventPayload{
			MessageID: record.ID, UserID: c.UserID,
			Content: p.Content, CreatedAt: record.CreatedAt,
		},
	})
	h.hub.Publish(ctx, c.RoomID, msg)
}

func (h *Handler) handlePing(ctx context.Context, c *hub.Client) {
	// presence TTL 갱신
	h.rdb.Expire(ctx, fmt.Sprintf("presence:%s:%d", c.RoomID, c.UserID), 30*time.Second)

	sendToClient(c, model.ServerMessage{Type: "pong", Payload: struct{}{}})
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

	nickname := ""
	if body.Data.Nickname != nil {
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

func sendToClient(c *hub.Client, msg model.ServerMessage) {
	data := mustMarshal(msg)
	select {
	case c.Send <- data:
	default:
	}
}

func mustMarshal(v any) []byte {
	data, _ := json.Marshal(v)
	return data
}
