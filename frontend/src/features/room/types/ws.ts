// 클라이언트 → 서버 메시지
export type ClientMessage =
  | { type: "move"; x: number; y: number }
  | { type: "chat"; content: string }
  | { type: "ping" };

// 서버 → 클라이언트 메시지
export type ServerMessage =
  | { type: "presence"; userId: number; x: number; y: number; nickname: string; avatarId: number | null }
  | { type: "chat"; messageId: number; userId: number; content: string; createdAt: string }
  | { type: "join"; userId: number; nickname: string }
  | { type: "leave"; userId: number }
  | { type: "pong" }
  | { type: "error"; code: string; message: string };

export type WsStatus = "disconnected" | "connecting" | "connected" | "error";

export interface PresenceEntry {
  userId: number;
  x: number;
  y: number;
  nickname: string;
  avatarId: number | null;
}

// 채팅 패널에서 사용하는 통합 메시지 타입
export type DisplayMessage =
  | { id: string; type: "chat"; userId: number; nickname: string; content: string; createdAt: string }
  | { id: string; type: "system"; content: string; createdAt: string };
