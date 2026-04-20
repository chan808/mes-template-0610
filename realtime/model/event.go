package model

// 클라이언트 → 서버 (flat)
type ClientMessage struct {
	Type    string   `json:"type"`
	X       *float64 `json:"x"`
	Y       *float64 `json:"y"`
	Content string   `json:"content"`
}

// 서버 → 클라이언트 (flat, per-type structs)

type PresenceEvent struct {
	Type     string  `json:"type"`
	UserID   int64   `json:"userId"`
	X        float64 `json:"x"`
	Y        float64 `json:"y"`
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
