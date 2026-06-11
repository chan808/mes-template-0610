import { MAP_COLS, MAP_ROWS, MapSpec } from "./tile";
import type { PlacedFurniture } from "../stores/furnitureStore";

export type { MapSpec };

// 맵 크기 결정 지점 — 구독 등급별 맵 크기는 여기서 분기한다.
// 주의: 서버(realtime/game/movement.go)가 같은 범위로 검증하므로
// 크기를 키우려면 방 데이터에 맵 크기를 싣고 서버 검증도 함께 바꿔야 한다.
export function resolveMapSpec(): MapSpec {
  return { cols: MAP_COLS, rows: MAP_ROWS };
}

// 방 기본 가구 배치 — 서버 맵 데이터 도입 전까지의 데모 레이아웃 (placedBy null = 방장만 편집 가능).
// 스폰 (15,10), 에이전트 슬롯 (22,5) (17,5) (22,12) (17,12)는 막지 않는다.
export const SEED_FURNITURE: PlacedFurniture[] = [
  { id: "seed-table-main", kind: "table-meeting", x: 13, y: 8, w: 4, h: 2, rotation: 0, passable: false, color: 0x92633c, label: "회의 테이블", placedBy: null },
  { id: "seed-sofa-1", kind: "sofa", x: 3, y: 3, w: 3, h: 1, rotation: 0, passable: false, color: 0x4663ac, label: "소파", placedBy: null },
  { id: "seed-shelf-1", kind: "shelf", x: 9, y: 0, w: 4, h: 1, rotation: 0, passable: false, color: 0x6b4f35, label: "책장", placedBy: null },
  { id: "seed-rug-1", kind: "rug", x: 2, y: 12, w: 4, h: 3, rotation: 0, passable: true, color: 0x3e6f5c, label: "러그", placedBy: null },
  { id: "seed-table-side", kind: "table-side", x: 3, y: 13, w: 2, h: 2, rotation: 0, passable: false, color: 0xa3744a, label: "탁자", placedBy: null },
  { id: "seed-plant-1", kind: "plant", x: 5, y: 7, w: 1, h: 1, rotation: 0, passable: false, color: 0x3f8f4f, placedBy: null },
  { id: "seed-plant-2", kind: "plant", x: 26, y: 2, w: 1, h: 1, rotation: 0, passable: false, color: 0x3f8f4f, placedBy: null },
  { id: "seed-plant-3", kind: "plant", x: 26, y: 17, w: 1, h: 1, rotation: 0, passable: false, color: 0x3f8f4f, placedBy: null },
];
