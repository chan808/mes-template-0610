package handler

import (
	"strings"
	"testing"

	"github.com/chan808/agolive-realtime/hub"
	"github.com/stretchr/testify/assert"
)

func makeAgents(nicknames ...string) []*hub.AgentState {
	agents := make([]*hub.AgentState, 0, len(nicknames))
	for i, n := range nicknames {
		agents = append(agents, &hub.AgentState{
			AgentID: string(rune('a' + i)), Nickname: n,
		})
	}
	return agents
}

func Test_멘션없는메시지_전체에이전트반환(t *testing.T) {
	agents := makeAgents("AI 도우미", "AI 요약자")

	targets := selectAgentTargets("안녕하세요 모두들", agents)

	assert.Len(t, targets, 2)
}

func Test_닉네임멘션_해당에이전트만반환(t *testing.T) {
	agents := makeAgents("AI 도우미", "AI 요약자")

	targets := selectAgentTargets("@AI 요약자 지금까지 회의 정리해줘", agents)

	assert.Len(t, targets, 1)
	assert.Equal(t, "AI 요약자", targets[0].Nickname)
}

func Test_이메일포함메시지_전체에이전트반환(t *testing.T) {
	// "@"가 있어도 등록된 닉네임 멘션이 아니면 전체에게 전달되어야 한다 (회귀 방지)
	agents := makeAgents("AI 도우미", "AI 요약자")

	targets := selectAgentTargets("제 메일은 chan@example.com 입니다", agents)

	assert.Len(t, targets, 2)
}

func Test_복수멘션_언급된에이전트만반환(t *testing.T) {
	agents := makeAgents("AI 도우미", "AI 요약자", "AI 검토자")

	targets := selectAgentTargets("@AI 도우미 @AI 검토자 의견 주세요", agents)

	assert.Len(t, targets, 2)
}

func Test_에이전트없음_빈목록반환(t *testing.T) {
	targets := selectAgentTargets("아무나 대답해줘", nil)

	assert.Empty(t, targets)
}

func Test_번호닉네임멘션_긴닉네임만매칭(t *testing.T) {
	// "@AI 도우미 2" 멘션이 prefix가 같은 "AI 도우미"에 중복 매칭되면 안 된다
	agents := makeAgents("AI 도우미", "AI 도우미 2")

	targets := selectAgentTargets("@AI 도우미 2 이것 좀 봐줘", agents)

	assert.Len(t, targets, 1)
	assert.Equal(t, "AI 도우미 2", targets[0].Nickname)
}

func Test_짧은닉네임멘션_번호닉네임_미매칭(t *testing.T) {
	agents := makeAgents("AI 도우미", "AI 도우미 2")

	targets := selectAgentTargets("@AI 도우미 안녕", agents)

	assert.Len(t, targets, 1)
	assert.Equal(t, "AI 도우미", targets[0].Nickname)
}

func int64Ptr(v int64) *int64 { return &v }

func Test_정상귓속말_검증통과(t *testing.T) {
	code, _, ok := validateWhisper(int64Ptr(20), "안녕", 10)

	assert.True(t, ok)
	assert.Empty(t, code)
}

func Test_타겟없는귓속말_검증실패(t *testing.T) {
	code, _, ok := validateWhisper(nil, "안녕", 10)

	assert.False(t, ok)
	assert.Equal(t, "INVALID_PAYLOAD", code)
}

func Test_빈내용귓속말_검증실패(t *testing.T) {
	code, _, ok := validateWhisper(int64Ptr(20), "", 10)

	assert.False(t, ok)
	assert.Equal(t, "INVALID_PAYLOAD", code)
}

func Test_길이초과귓속말_검증실패(t *testing.T) {
	// 500자(rune) 초과는 거부 — 멀티바이트 기준으로 센다
	long := strings.Repeat("가", 501)

	code, _, ok := validateWhisper(int64Ptr(20), long, 10)

	assert.False(t, ok)
	assert.Equal(t, "INVALID_PAYLOAD", code)
}

func Test_길이상한귓속말_검증통과(t *testing.T) {
	exact := strings.Repeat("가", 500)

	_, _, ok := validateWhisper(int64Ptr(20), exact, 10)

	assert.True(t, ok)
}

func Test_자기자신귓속말_검증실패(t *testing.T) {
	code, _, ok := validateWhisper(int64Ptr(10), "안녕", 10)

	assert.False(t, ok)
	assert.Equal(t, "WHISPER_SELF", code)
}
