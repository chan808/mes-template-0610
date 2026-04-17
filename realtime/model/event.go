package model

import "encoding/json"

// 클라이언트 → 서버
type ClientMessage struct {
	Type    string          `json:"type"`
	Payload json.RawMessage `json:"payload,omitempty"`
}

type MovePayload struct {
	X float64 `json:"x"`
	Y float64 `json:"y"`
}

type ChatPayload struct {
	Content string `json:"content"`
}

// 서버 → 클라이언트
type ServerMessage struct {
	Type    string `json:"type"`
	Payload any    `json:"payload"`
}

type PresencePayload struct {
	UserID   int64   `json:"userId"`
	X        float64 `json:"x"`
	Y        float64 `json:"y"`
	Nickname string  `json:"nickname"`
	AvatarID *int64  `json:"avatarId"`
}

type ChatEventPayload struct {
	MessageID int64  `json:"messageId"`
	UserID    int64  `json:"userId"`
	Content   string `json:"content"`
	CreatedAt string `json:"createdAt"`
}

type JoinPayload struct {
	UserID   int64  `json:"userId"`
	Nickname string `json:"nickname"`
}

type LeavePayload struct {
	UserID int64 `json:"userId"`
}

type ErrorPayload struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
