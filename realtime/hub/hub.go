package hub

import (
	"context"
	"sync"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/redis/go-redis/v9"
)

var wsActiveConnections = promauto.NewGauge(prometheus.GaugeOpts{
	Name: "ws_active_connections_total",
	Help: "현재 활성 WebSocket 연결 수",
})

type Client struct {
	UserID   int64
	Nickname string
	AvatarID *int64
	RoomID   string
	Send     chan []byte
	done     chan struct{}
	doneOnce sync.Once
}

func NewClient(userID int64, nickname string, avatarID *int64, roomID string) *Client {
	return &Client{
		UserID:   userID,
		Nickname: nickname,
		AvatarID: avatarID,
		RoomID:   roomID,
		Send:     make(chan []byte, 256),
		done:     make(chan struct{}),
	}
}

// Done은 클라이언트가 종료 신호를 받을 때 닫히는 채널을 반환한다
func (c *Client) Done() <-chan struct{} {
	return c.done
}

// SignalDone은 클라이언트 종료를 알린다 (멱등성 보장)
func (c *Client) SignalDone() {
	c.doneOnce.Do(func() { close(c.done) })
}

type AgentState struct {
	AgentID  string
	CancelFn context.CancelFunc
}

type roomState struct {
	clients map[*Client]bool
	ps      *redis.PubSub
	agent   *AgentState
}

type Hub struct {
	mu    sync.RWMutex
	rooms map[string]*roomState
	rdb   *redis.Client
}

func New(rdb *redis.Client) *Hub {
	return &Hub{
		rooms: make(map[string]*roomState),
		rdb:   rdb,
	}
}

// Join은 클라이언트를 룸에 등록하고 첫 입장 시 Redis Pub/Sub 구독을 시작한다
func (h *Hub) Join(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()

	r, ok := h.rooms[c.RoomID]
	if !ok {
		r = &roomState{clients: make(map[*Client]bool)}
		h.rooms[c.RoomID] = r
		h.startSubscriber(c.RoomID, r)
	}
	r.clients[c] = true
	wsActiveConnections.Inc()
}

// Leave는 클라이언트를 룸에서 제거한다.
// 마지막 클라이언트 퇴장으로 룸이 비워지면서 에이전트가 있었다면 해당 AgentState를 반환한다.
func (h *Hub) Leave(c *Client) *AgentState {
	h.mu.Lock()
	defer h.mu.Unlock()

	r, ok := h.rooms[c.RoomID]
	if !ok {
		return nil
	}
	delete(r.clients, c)
	c.SignalDone()
	wsActiveConnections.Dec()
	if len(r.clients) == 0 {
		orphaned := r.agent
		r.ps.Close()
		delete(h.rooms, c.RoomID)
		return orphaned
	}
	return nil
}

// Publish는 룸 채널에 메시지를 발행한다
func (h *Hub) Publish(ctx context.Context, roomID string, data []byte) {
	h.rdb.Publish(ctx, "room:"+roomID, string(data))
}

// SetAgent는 룸에 에이전트 세션을 등록한다
func (h *Hub) SetAgent(roomID string, state *AgentState) {
	h.mu.Lock()
	defer h.mu.Unlock()
	r, ok := h.rooms[roomID]
	if ok {
		r.agent = state
	}
}

// GetAgent는 룸의 에이전트 세션을 반환한다
func (h *Hub) GetAgent(roomID string) *AgentState {
	h.mu.RLock()
	defer h.mu.RUnlock()
	if r, ok := h.rooms[roomID]; ok {
		return r.agent
	}
	return nil
}

// ClearAgent는 룸의 에이전트 세션을 제거한다
func (h *Hub) ClearAgent(roomID string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if r, ok := h.rooms[roomID]; ok {
		r.agent = nil
	}
}

// HasAgent는 룸에 에이전트가 있는지 확인한다
func (h *Hub) HasAgent(roomID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	if r, ok := h.rooms[roomID]; ok {
		return r.agent != nil
	}
	return false
}

func (h *Hub) startSubscriber(roomID string, r *roomState) {
	ps := h.rdb.Subscribe(context.Background(), "room:"+roomID)
	r.ps = ps
	go func() {
		for msg := range ps.Channel() {
			h.broadcast(roomID, []byte(msg.Payload))
		}
	}()
}

func (h *Hub) broadcast(roomID string, data []byte) {
	h.mu.RLock()
	defer h.mu.RUnlock()

	r, ok := h.rooms[roomID]
	if !ok {
		return
	}
	for c := range r.clients {
		select {
		case c.Send <- data:
		default:
			// 버퍼 가득 참: 메시지 드롭
		}
	}
}
