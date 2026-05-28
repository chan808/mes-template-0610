import { create } from "zustand";
import { HumanInputRequest, WsStatus } from "../types/ws";

interface WsState {
  status: WsStatus;
  ws: WebSocket | null;
  humanInputRequest: HumanInputRequest | null;
  setStatus: (status: WsStatus) => void;
  setWs: (ws: WebSocket | null) => void;
  setHumanInputRequest: (req: HumanInputRequest | null) => void;
  reset: () => void;
}

export const useWsStore = create<WsState>((set) => ({
  status: "disconnected",
  ws: null,
  humanInputRequest: null,
  setStatus: (status) => set({ status }),
  setWs: (ws) => set({ ws }),
  setHumanInputRequest: (req) => set({ humanInputRequest: req }),
  reset: () => set({ status: "disconnected", ws: null, humanInputRequest: null }),
}));
