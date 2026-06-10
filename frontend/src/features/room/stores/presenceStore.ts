import { create } from "zustand";
import { AgentEntry, PresenceEntry } from "../types/ws";
import { Direction, SPAWN_TILE, TilePos } from "../lib/tile";

interface PresenceState {
  presences: Map<number, PresenceEntry>;
  agents: Map<string, AgentEntry>;
  // presence/join 이벤트로 수집한 닉네임 캐시 — removePresence 후에도 유지
  nicknameCache: Map<number, string>;
  myTile: TilePos;
  myDir: Direction;
  upsertPresence: (entry: PresenceEntry) => void;
  removePresence: (userId: number) => void;
  setMyTile: (x: number, y: number, dir: Direction) => void;
  adoptServerPosition: (x: number, y: number, dir: Direction) => void;
  cacheNickname: (userId: number, nickname: string) => void;
  getNickname: (userId: number) => string;
  upsertAgent: (entry: AgentEntry) => void;
  removeAgent: (agentId: string) => void;
  clear: () => void;
}

export const usePresenceStore = create<PresenceState>((set, get) => ({
  presences: new Map(),
  agents: new Map(),
  nicknameCache: new Map(),
  myTile: { ...SPAWN_TILE },
  myDir: "down",

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

  setMyTile: (x, y, dir) => set({ myTile: { x, y }, myDir: dir }),

  // 내 presence 에코/보정 처리: 한 칸 차이는 전송 지연 중인 에코로 보고 무시,
  // 두 칸 이상 어긋나면 서버 권위 위치로 스냅 (이동 거부·재접속 후 동기화)
  adoptServerPosition: (x, y, dir) =>
    set((state) => {
      const dx = Math.abs(state.myTile.x - x);
      const dy = Math.abs(state.myTile.y - y);
      if (dx <= 1 && dy <= 1) return {};
      return { myTile: { x, y }, myDir: dir };
    }),

  cacheNickname: (userId, nickname) =>
    set((state) => {
      const next = new Map(state.nicknameCache);
      next.set(userId, nickname);
      return { nicknameCache: next };
    }),

  getNickname: (userId) =>
    get().nicknameCache.get(userId) ?? "알 수 없음",

  upsertAgent: (entry) =>
    set((state) => {
      const next = new Map(state.agents);
      next.set(entry.agentId, entry);
      return { agents: next };
    }),

  removeAgent: (agentId) =>
    set((state) => {
      const next = new Map(state.agents);
      next.delete(agentId);
      return { agents: next };
    }),

  clear: () =>
    set({
      presences: new Map(),
      agents: new Map(),
      nicknameCache: new Map(),
      myTile: { ...SPAWN_TILE },
      myDir: "down",
    }),
}));
