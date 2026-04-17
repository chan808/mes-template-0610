import { create } from "zustand";
import { PresenceEntry } from "../types/ws";

const CANVAS_WIDTH = 1200;
const CANVAS_HEIGHT = 800;
const AVATAR_SIZE = 40;

interface PresenceState {
  presences: Map<number, PresenceEntry>;
  // presence/join 이벤트로 수집한 닉네임 캐시 — removePresence 후에도 유지
  nicknameCache: Map<number, string>;
  myPosition: { x: number; y: number };
  upsertPresence: (entry: PresenceEntry) => void;
  removePresence: (userId: number) => void;
  setMyPosition: (x: number, y: number) => void;
  cacheNickname: (userId: number, nickname: string) => void;
  getNickname: (userId: number) => string;
  clear: () => void;
}

export const usePresenceStore = create<PresenceState>((set, get) => ({
  presences: new Map(),
  nicknameCache: new Map(),
  myPosition: { x: CANVAS_WIDTH / 2, y: CANVAS_HEIGHT / 2 },

  upsertPresence: (entry) =>
    set((state) => {
      const nextPresences = new Map(state.presences);
      nextPresences.set(entry.userId, entry);
      const nextCache = new Map(state.nicknameCache);
      nextCache.set(entry.userId, entry.nickname);
      return { presences: nextPresences, nicknameCache: nextCache };
    }),

  removePresence: (userId) =>
    set((state) => {
      const next = new Map(state.presences);
      next.delete(userId);
      return { presences: next };
      // nicknameCache는 유지 — 이후 채팅 메시지에서 닉네임 조회 가능
    }),

  setMyPosition: (x, y) =>
    set({
      myPosition: {
        x: Math.max(AVATAR_SIZE / 2, Math.min(CANVAS_WIDTH - AVATAR_SIZE / 2, x)),
        y: Math.max(AVATAR_SIZE / 2, Math.min(CANVAS_HEIGHT - AVATAR_SIZE / 2, y)),
      },
    }),

  cacheNickname: (userId, nickname) =>
    set((state) => {
      const next = new Map(state.nicknameCache);
      next.set(userId, nickname);
      return { nicknameCache: next };
    }),

  getNickname: (userId) =>
    get().nicknameCache.get(userId) ?? "알 수 없음",

  clear: () =>
    set({
      presences: new Map(),
      nicknameCache: new Map(),
      myPosition: { x: CANVAS_WIDTH / 2, y: CANVAS_HEIGHT / 2 },
    }),
}));

export { CANVAS_WIDTH, CANVAS_HEIGHT, AVATAR_SIZE };
