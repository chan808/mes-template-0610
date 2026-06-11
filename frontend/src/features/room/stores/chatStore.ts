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

      // 완료 시 ID를 고유값으로 확정해 같은 에이전트의 다음 턴이 새 말풍선으로 시작되게 한다
      const finalId = `agent-${agentId}-${Date.now()}`;

      if (!existing) {
        // 내용 없이 완료만 알리는 이벤트는 무시 (빈 말풍선 방지)
        if (done && !content) return state;
        // 첫 청크: 스트리밍 메시지 생성
        const newMsg: DisplayMessage = {
          id: done ? finalId : streamId,
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
            ? { ...m, id: done ? finalId : streamId, content: m.content + content, streaming: !done }
            : m,
        ),
      };
    }),

  clear: () => set({ messages: [], hasMore: false, oldestCursor: null }),
}));
