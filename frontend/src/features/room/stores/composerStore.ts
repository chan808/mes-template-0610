import { create } from "zustand";

// 채팅 입력 타겟 — 아바타 더블클릭으로 설정 (ADR-0002)
// user: 귓속말 모드, agent: @멘션 단축키 (대화는 방 전체 공개)
export type ChatTarget =
  | { kind: "user"; userId: number; nickname: string }
  | { kind: "agent"; nickname: string };

interface ComposerState {
  target: ChatTarget | null;
  setTarget: (target: ChatTarget) => void;
  clearTarget: () => void;
}

export const useComposerStore = create<ComposerState>((set) => ({
  target: null,
  setTarget: (target) => set({ target }),
  clearTarget: () => set({ target: null }),
}));
