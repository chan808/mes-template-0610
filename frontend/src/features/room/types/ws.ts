// 클라이언트 → 서버 메시지
export type ClientMessage =
  | { type: "move"; x: number; y: number }
  | { type: "chat"; content: string }
  | { type: "ping" }
  | { type: "summon_agent"; role: AgentRole }
  | { type: "dismiss_agent"; agentId: string }
  | { type: "agent_input"; agentId: string; response: string };

export type AgentRole = "helper" | "summarizer" | "researcher" | "critic" | "orchestrator";

// 서버 → 클라이언트 메시지
export type ServerMessage =
  | { type: "presence"; userId: number; x: number; y: number; nickname: string; avatarId: number | null }
  | { type: "chat"; messageId: number; userId: number; content: string; createdAt: string }
  | { type: "join"; userId: number; nickname: string }
  | { type: "leave"; userId: number }
  | { type: "pong" }
  | { type: "error"; code: string; message: string }
  | { type: "agent_joined"; agentId: string; role: string; nickname: string; x: number; y: number }
  | { type: "agent_left"; agentId: string }
  | { type: "agent_message"; agentId: string; content: string; done: boolean }
  | { type: "agent_needs_input"; agentId: string; toolUseId: string; prompt: string; options: string[] }
  | { type: "agent_thinking"; agentId: string; step: string }
  | { type: "agent_file"; agentId: string; filename: string; url: string; mimeType: string };

export type WsStatus = "disconnected" | "connecting" | "connected" | "error";

export interface PresenceEntry {
  userId: number;
  x: number;
  y: number;
  nickname: string;
  avatarId: number | null;
}

export interface AgentEntry {
  agentId: string;
  role: string;
  nickname: string;
  x: number;
  y: number;
}

// 채팅 패널에서 사용하는 통합 메시지 타입
export type DisplayMessage =
  | { id: string; type: "chat"; userId: number; nickname: string; content: string; createdAt: string }
  | { id: string; type: "system"; content: string; createdAt: string }
  | { id: string; type: "agent"; agentId: string; nickname: string; content: string; createdAt: string; streaming: boolean }
  | { id: string; type: "file"; agentId: string; nickname: string; filename: string; url: string; mimeType: string; createdAt: string };

// HitL 다이얼로그 상태
export interface HumanInputRequest {
  agentId: string;
  toolUseId: string;
  agentNickname: string;
  prompt: string;
  options: string[];
}
