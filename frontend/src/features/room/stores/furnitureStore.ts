import { create } from "zustand";
import { Furniture, Rotation, canPlace } from "../lib/furniture";
import type { MapSpec } from "../lib/tile";

// 배치된 가구: placedBy로 편집 권한을 판정한다 (null = 방 기본 가구)
export interface PlacedFurniture extends Furniture {
  rotation: Rotation;
  placedBy: number | null;
}

// 편집 권한: 방장은 전체, 멤버는 본인이 배치한 가구만, 기본 가구는 방장만
export function canEditFurniture(item: PlacedFurniture, actorId: number, roomOwnerId: number): boolean {
  if (actorId === roomOwnerId) return true;
  return item.placedBy === actorId;
}

interface MovePayload {
  x: number;
  y: number;
  rotation: Rotation;
}

// 가구 상태 저장소. place/move/remove는 추후 WS 이벤트(place/move/remove_furniture)와 1:1 대응 —
// 서버 연동 시 액션 내부에서 send를 호출하고, 서버 브로드캐스트로 타 클라이언트와 동기화한다
interface FurnitureState {
  items: Map<string, PlacedFurniture>;
  seeded: boolean;
  seed: (items: PlacedFurniture[]) => void;
  place: (item: PlacedFurniture, spec: MapSpec) => boolean;
  move: (id: string, to: MovePayload, actorId: number, roomOwnerId: number, spec: MapSpec) => boolean;
  remove: (id: string, actorId: number, roomOwnerId: number) => boolean;
  reset: () => void;
}

export const useFurnitureStore = create<FurnitureState>((set, get) => ({
  items: new Map(),
  seeded: false,

  // 방 기본 레이아웃 주입 — 최초 1회만 (서버 맵 데이터 도입 시 입장 응답으로 대체)
  seed: (items) =>
    set((state) => {
      if (state.seeded) return {};
      return { items: new Map(items.map((i) => [i.id, i])), seeded: true };
    }),

  place: (item, spec) => {
    const { items } = get();
    if (items.has(item.id)) return false;
    if (!canPlace([...items.values()], spec, { ...item, rotation: item.rotation })) return false;

    const next = new Map(items);
    next.set(item.id, item);
    set({ items: next });
    return true;
  },

  move: (id, to, actorId, roomOwnerId, spec) => {
    const { items } = get();
    const item = items.get(id);
    if (!item) return false;
    if (!canEditFurniture(item, actorId, roomOwnerId)) return false;
    if (!canPlace([...items.values()], spec, { ...item, ...to, excludeId: id })) return false;

    const next = new Map(items);
    next.set(id, { ...item, ...to });
    set({ items: next });
    return true;
  },

  remove: (id, actorId, roomOwnerId) => {
    const { items } = get();
    const item = items.get(id);
    if (!item) return false;
    if (!canEditFurniture(item, actorId, roomOwnerId)) return false;

    const next = new Map(items);
    next.delete(id);
    set({ items: next });
    return true;
  },

  reset: () => set({ items: new Map(), seeded: false }),
}));
