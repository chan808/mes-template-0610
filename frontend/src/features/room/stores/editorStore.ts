import { create } from "zustand";
import { Rotation } from "../lib/furniture";
import { CatalogEntry } from "../lib/furnitureCatalog";
import { TilePos } from "../lib/tile";

// 드래그 세션: palette = 신규 배치(놓으면 place), placed = 기존 가구 이동(놓으면 move)
export interface DragState {
  mode: "palette" | "placed";
  id: string | null; // placed 모드일 때 이동 중인 가구 id
  entry: CatalogEntry;
  rotation: Rotation;
  tile: TilePos | null; // 고스트 footprint 좌상단 (캔버스 밖이면 null)
  valid: boolean;
  overZone: boolean; // 취소/삭제 드롭존 위 호버
}

// 가구 편집 모드 UI 상태 — 패널이 열려 있는 동안만 격자 표시·가구 드래그 활성화
interface EditorState {
  panelOpen: boolean;
  drag: DragState | null;
  togglePanel: () => void;
  closePanel: () => void;
  startPaletteDrag: (entry: CatalogEntry) => void;
  startPlacedDrag: (id: string, entry: CatalogEntry, rotation: Rotation) => void;
  setDragTile: (tile: TilePos | null, valid: boolean) => void;
  setOverZone: (over: boolean) => void;
  rotateDrag: () => void;
  endDrag: () => void;
}

export const useEditorStore = create<EditorState>((set) => ({
  panelOpen: false,
  drag: null,

  togglePanel: () => set((s) => ({ panelOpen: !s.panelOpen, drag: null })),
  closePanel: () => set({ panelOpen: false, drag: null }),

  startPaletteDrag: (entry) =>
    set({ drag: { mode: "palette", id: null, entry, rotation: 0, tile: null, valid: false, overZone: false } }),

  startPlacedDrag: (id, entry, rotation) =>
    set({ drag: { mode: "placed", id, entry, rotation, tile: null, valid: false, overZone: false } }),

  setDragTile: (tile, valid) =>
    set((s) => (s.drag ? { drag: { ...s.drag, tile, valid } } : {})),

  setOverZone: (over) =>
    set((s) => (s.drag && s.drag.overZone !== over ? { drag: { ...s.drag, overZone: over } } : {})),

  // R 키: 90도 회전
  rotateDrag: () =>
    set((s) => (s.drag ? { drag: { ...s.drag, rotation: ((s.drag.rotation + 1) % 4) as Rotation } } : {})),

  endDrag: () => set({ drag: null }),
}));
