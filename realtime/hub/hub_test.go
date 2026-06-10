package hub

import (
	"context"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
)

const testMaxAgents = 4

// newTestHub은 Redis 구독 없이 룸 상태를 직접 구성한 Hub를 만든다
func newTestHub(roomID string) *Hub {
	h := New(nil)
	h.rooms[roomID] = &roomState{
		clients:       make(map[*Client]bool),
		agents:        make(map[string]*AgentState),
		reservedSlots: make(map[int]bool),
	}
	return h
}

func newTestAgent(id string, slot int) *AgentState {
	ctx, cancel := context.WithCancel(context.Background())
	return &AgentState{
		AgentID: id, Role: "helper", Nickname: "AI 도우미",
		Slot: slot, Ctx: ctx, CancelFn: cancel,
		HumanInputCh: make(chan string, 1),
	}
}

func Test_빈룸_슬롯예약_성공(t *testing.T) {
	h := newTestHub("1")

	slot, ok := h.TryReserveAgentSlot("1", testMaxAgents)

	assert.True(t, ok)
	assert.Equal(t, 0, slot)
}

func Test_존재하지않는룸_슬롯예약_실패(t *testing.T) {
	h := New(nil)

	_, ok := h.TryReserveAgentSlot("없는방", testMaxAgents)

	assert.False(t, ok)
}

func Test_정원도달_슬롯예약_실패(t *testing.T) {
	h := newTestHub("1")
	for i := 0; i < testMaxAgents; i++ {
		slot, ok := h.TryReserveAgentSlot("1", testMaxAgents)
		assert.True(t, ok)
		h.CommitAgent("1", newTestAgent(string(rune('a'+i)), slot))
	}

	_, ok := h.TryReserveAgentSlot("1", testMaxAgents)

	assert.False(t, ok)
	assert.Equal(t, testMaxAgents, h.AgentCount("1"))
}

func Test_예약만으로도_정원계산에_포함(t *testing.T) {
	h := newTestHub("1")
	for i := 0; i < testMaxAgents; i++ {
		_, ok := h.TryReserveAgentSlot("1", testMaxAgents)
		assert.True(t, ok)
	}

	// 커밋 전이라도 예약 슬롯이 정원을 차지한다
	_, ok := h.TryReserveAgentSlot("1", testMaxAgents)

	assert.False(t, ok)
}

func Test_예약해제후_재예약_성공(t *testing.T) {
	h := newTestHub("1")
	slot, _ := h.TryReserveAgentSlot("1", testMaxAgents)

	h.ReleaseAgentSlot("1", slot)
	again, ok := h.TryReserveAgentSlot("1", testMaxAgents)

	assert.True(t, ok)
	assert.Equal(t, slot, again)
}

func Test_에이전트제거후_슬롯재사용_가능(t *testing.T) {
	h := newTestHub("1")
	slot0, _ := h.TryReserveAgentSlot("1", testMaxAgents)
	h.CommitAgent("1", newTestAgent("a", slot0))
	slot1, _ := h.TryReserveAgentSlot("1", testMaxAgents)
	h.CommitAgent("1", newTestAgent("b", slot1))

	h.RemoveAgent("1", "a")
	reused, ok := h.TryReserveAgentSlot("1", testMaxAgents)

	// 제거된 에이전트의 슬롯(0)이 가장 낮은 빈 슬롯으로 재사용된다
	assert.True(t, ok)
	assert.Equal(t, slot0, reused)
}

func Test_동시예약_정원초과없음(t *testing.T) {
	h := newTestHub("1")
	var wg sync.WaitGroup
	var mu sync.Mutex
	granted := make(map[int]int) // slot → 예약 횟수

	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if slot, ok := h.TryReserveAgentSlot("1", testMaxAgents); ok {
				mu.Lock()
				granted[slot]++
				mu.Unlock()
			}
		}()
	}
	wg.Wait()

	// 정확히 max개만 예약되고 슬롯 중복이 없어야 한다
	assert.Len(t, granted, testMaxAgents)
	for slot, count := range granted {
		assert.Equal(t, 1, count, "슬롯 %d 중복 예약", slot)
	}
}

func Test_커밋된에이전트_조회_성공(t *testing.T) {
	h := newTestHub("1")
	slot, _ := h.TryReserveAgentSlot("1", testMaxAgents)
	h.CommitAgent("1", newTestAgent("a", slot))

	agent := h.GetAgent("1", "a")

	assert.NotNil(t, agent)
	assert.Equal(t, slot, agent.Slot)
	assert.Len(t, h.GetAgents("1"), 1)
}
