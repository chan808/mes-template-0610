import { create } from "zustand";
import { PresenceEntry } from "../types/ws";

const CANVAS_WIDTH = 1200;
const CANVAS_HEIGHT = 800;
const AVATAR_SIZE = 40;

interface PresenceState {
  presences: Map<number, PresenceEntry>;
  myPosition: { x: number; y: number };
  upsertPresence: (entry: PresenceEntry) => void;
  removePresence: (userId: number) => void;
  setMyPosition: (x: number, y: number) => void;
  clear: () => void;
}

export const usePresenceStore = create<PresenceState>((set) => ({
  presences: new Map(),
  myPosition: { x: CANVAS_WIDTH / 2, y: CANVAS_HEIGHT / 2 },

  upsertPresence: (entry) =>
    set((state) => {
      const next = new Map(state.presences);
      next.set(entry.userId, entry);
      return { presences: next };
    }),

  removePresence: (userId) =>
    set((state) => {
      const next = new Map(state.presences);
      next.delete(userId);
      return { presences: next };
    }),

  setMyPosition: (x, y) =>
    set({
      myPosition: {
        x: Math.max(AVATAR_SIZE / 2, Math.min(CANVAS_WIDTH - AVATAR_SIZE / 2, x)),
        y: Math.max(AVATAR_SIZE / 2, Math.min(CANVAS_HEIGHT - AVATAR_SIZE / 2, y)),
      },
    }),

  clear: () =>
    set({ presences: new Map(), myPosition: { x: CANVAS_WIDTH / 2, y: CANVAS_HEIGHT / 2 } }),
}));

export { CANVAS_WIDTH, CANVAS_HEIGHT, AVATAR_SIZE };
