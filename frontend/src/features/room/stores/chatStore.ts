import { create } from "zustand";
import { DisplayMessage } from "../types/ws";

interface ChatState {
  messages: DisplayMessage[];
  hasMore: boolean;
  oldestCursor: number | null;
  prependHistory: (messages: DisplayMessage[], hasMore: boolean, cursor: number | null) => void;
  appendMessage: (message: DisplayMessage) => void;
  clear: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  messages: [],
  hasMore: false,
  oldestCursor: null,

  prependHistory: (messages, hasMore, cursor) =>
    set((state) => ({
      // 기존 메시지 앞에 히스토리 추가 (중복 id 제거)
      messages: [
        ...messages.filter((m) => !state.messages.some((existing) => existing.id === m.id)),
        ...state.messages,
      ],
      hasMore,
      oldestCursor: cursor,
    })),

  appendMessage: (message) =>
    set((state) => ({ messages: [...state.messages, message] })),

  clear: () => set({ messages: [], hasMore: false, oldestCursor: null }),
}));
