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
	AgentID      string
	Role         string
	Nickname     string
	X            float64
	Y            float64
	CancelFn     context.CancelFunc
	HumanInputCh chan string // HitL: handleAgentInput → streamAgentResponse 응답 전달
}

type roomState struct {
	clients map[*Client]bool
	ps      *redis.PubSub
	agents  map[string]*AgentState
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
		r = &roomState{
			clients: make(map[*Client]bool),
			agents:  make(map[string]*AgentState),
		}
		h.rooms[c.RoomID] = r
		h.startSubscriber(c.RoomID, r)
	}
	r.clients[c] = true
	wsActiveConnections.Inc()
}

// Leave는 클라이언트를 룸에서 제거한다.
// 마지막 클라이언트 퇴장으로 룸이 비면 고아 에이전트 목록을 반환한다.
func (h *Hub) Leave(c *Client) []*AgentState {
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
		orphaned := make([]*AgentState, 0, len(r.agents))
		for _, a := range r.agents {
			orphaned = append(orphaned, a)
		}
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

// AddAgent는 룸에 에이전트를 등록한다
func (h *Hub) AddAgent(roomID string, state *AgentState) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if r, ok := h.rooms[roomID]; ok {
		r.agents[state.AgentID] = state
	}
}

// RemoveAgent는 룸에서 특정 에이전트를 제거하고 반환한다
func (h *Hub) RemoveAgent(roomID, agentID string) *AgentState {
	h.mu.Lock()
	defer h.mu.Unlock()
	if r, ok := h.rooms[roomID]; ok {
		a := r.agents[agentID]
		delete(r.agents, agentID)
		return a
	}
	return nil
}

// GetAgents는 룸의 모든 에이전트 목록을 반환한다
func (h *Hub) GetAgents(roomID string) []*AgentState {
	h.mu.RLock()
	defer h.mu.RUnlock()
	if r, ok := h.rooms[roomID]; ok {
		agents := make([]*AgentState, 0, len(r.agents))
		for _, a := range r.agents {
			agents = append(agents, a)
		}
		return agents
	}
	return nil
}

// GetAgent는 룸의 특정 에이전트를 반환한다
func (h *Hub) GetAgent(roomID, agentID string) *AgentState {
	h.mu.RLock()
	defer h.mu.RUnlock()
	if r, ok := h.rooms[roomID]; ok {
		return r.agents[agentID]
	}
	return nil
}

// AgentCount는 룸의 현재 에이전트 수를 반환한다
func (h *Hub) AgentCount(roomID string) int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	if r, ok := h.rooms[roomID]; ok {
		return len(r.agents)
	}
	return 0
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
