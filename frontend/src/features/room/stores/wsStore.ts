import { create } from "zustand";
import { WsStatus } from "../types/ws";

interface WsState {
  status: WsStatus;
  ws: WebSocket | null;
  setStatus: (status: WsStatus) => void;
  setWs: (ws: WebSocket | null) => void;
  reset: () => void;
}

export const useWsStore = create<WsState>((set) => ({
  status: "disconnected",
  ws: null,
  setStatus: (status) => set({ status }),
  setWs: (ws) => set({ ws }),
  reset: () => set({ status: "disconnected", ws: null }),
}));
