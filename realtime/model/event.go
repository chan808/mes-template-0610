package model

// 클라이언트 → 서버 (flat)
type ClientMessage struct {
	Type         string   `json:"type"`
	X            *float64 `json:"x"`
	Y            *float64 `json:"y"`
	Dir          string   `json:"dir"` // move: up/down/left/right
	Content      string   `json:"content"`
	Role         string   `json:"role"`         // summon_agent
	AgentID      string   `json:"agentId"`      // dismiss_agent, agent_input
	Response     string   `json:"response"`     // agent_input
	TargetUserID *int64   `json:"targetUserId"` // whisper
}

// 서버 → 클라이언트 (flat, per-type structs)

type PresenceEvent struct {
	Type     string  `json:"type"`
	UserID   int64   `json:"userId"`
	X        float64 `json:"x"` // 타일 좌표 (0 ≤ x < game.MapCols)
	Y        float64 `json:"y"` // 타일 좌표 (0 ≤ y < game.MapRows)
	Dir      string  `json:"dir"`
	Nickname string  `json:"nickname"`
	AvatarID *int64  `json:"avatarId"`
}

type ChatEvent struct {
	Type      string `json:"type"`
	MessageID int64  `json:"messageId"`
	UserID    int64  `json:"userId"`
	Content   string `json:"content"`
	CreatedAt string `json:"createdAt"`
}

// 귓속말 — 발신자와 수신자에게만 전송, DB 미저장 (ADR-0002)
type WhisperEvent struct {
	Type       string `json:"type"`
	FromUserID int64  `json:"fromUserId"`
	ToUserID   int64  `json:"toUserId"`
	Nickname   string `json:"nickname"` // 발신자 닉네임
	Content    string `json:"content"`
	CreatedAt  string `json:"createdAt"`
}

type JoinEvent struct {
	Type     string `json:"type"`
	UserID   int64  `json:"userId"`
	Nickname string `json:"nickname"`
}

type LeaveEvent struct {
	Type   string `json:"type"`
	UserID int64  `json:"userId"`
}

type PongEvent struct {
	Type string `json:"type"`
}

type ErrorEvent struct {
	Type    string `json:"type"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

type AgentJoinedEvent struct {
	Type     string  `json:"type"`
	AgentID  string  `json:"agentId"`
	Role     string  `json:"role"`
	Nickname string  `json:"nickname"`
	X        float64 `json:"x"`
	Y        float64 `json:"y"`
}

type AgentLeftEvent struct {
	Type    string `json:"type"`
	AgentID string `json:"agentId"`
}

type AgentMessageEvent struct {
	Type    string `json:"type"`
	AgentID string `json:"agentId"`
	Content string `json:"content"`
	Done    bool   `json:"done"`
}

// HitL: 에이전트가 사용자 입력을 요청할 때 브로드캐스트
type AgentNeedsInputEvent struct {
	Type      string   `json:"type"`
	AgentID   string   `json:"agentId"`
	ToolUseID string   `json:"toolUseId"`
	Prompt    string   `json:"prompt"`
	Options   []string `json:"options"`
}

// 오케스트레이터 진행 상황 표시
type AgentThinkingEvent struct {
	Type    string `json:"type"`
	AgentID string `json:"agentId"`
	Step    string `json:"step"`
}

// 에이전트가 생성한 파일 다운로드 링크
type AgentFileEvent struct {
	Type     string `json:"type"`
	AgentID  string `json:"agentId"`
	Filename string `json:"filename"`
	URL      string `json:"url"`
	MimeType string `json:"mimeType"`
}
