import { create } from "zustand";
import { DisplayMessage } from "../types/ws";

interface ChatState {
  messages: DisplayMessage[];
  hasMore: boolean;
  oldestCursor: number | null;
  prependHistory: (messages: DisplayMessage[], hasMore: boolean, cursor: number | null) => void;
  appendMessage: (message: DisplayMessage) => void;
  // 스트리밍 중인 에이전트 메시지를 id 기준으로 누적/확정
  appendAgentChunk: (agentId: string, nickname: string, content: string, done: boolean) => void;
  clear: () => void;
}

const STREAMING_ID_PREFIX = "agent-stream-";

export const useChatStore = create<ChatState>((set) => ({
  messages: [],
  hasMore: false,
  oldestCursor: null,

  prependHistory: (messages, hasMore, cursor) =>
    set((state) => ({
      messages: [
        ...messages.filter((m) => !state.messages.some((existing) => existing.id === m.id)),
        ...state.messages,
      ],
      hasMore,
      oldestCursor: cursor,
    })),

  appendMessage: (message) =>
    set((state) => ({ messages: [...state.messages, message] })),

  appendAgentChunk: (agentId, nickname, content, done) =>
    set((state) => {
      const streamId = `${STREAMING_ID_PREFIX}${agentId}`;
      const existing = state.messages.find((m) => m.id === streamId);

      if (!existing) {
        // 첫 청크: 스트리밍 메시지 생성
        const newMsg: DisplayMessage = {
          id: streamId,
          type: "agent",
          agentId,
          nickname,
          content,
          createdAt: new Date().toISOString(),
          streaming: !done,
        };
        return { messages: [...state.messages, newMsg] };
      }

      // 이후 청크: 누적 또는 확정
      return {
        messages: state.messages.map((m) =>
          m.id === streamId && m.type === "agent"
            ? { ...m, content: m.content + content, streaming: !done }
            : m,
        ),
      };
    }),

  clear: () => set({ messages: [], hasMore: false, oldestCursor: null }),
}));
