import { Rotation } from "./furniture";

// 배치 가능한 가구 카탈로그 — 스프라이트/서버 카탈로그 도입 전 임시 데이터
export interface CatalogEntry {
  kind: string;
  label: string;
  w: number;
  h: number;
  passable: boolean;
  color: number;
}

export const FURNITURE_CATALOG: CatalogEntry[] = [
  { kind: "table-meeting", label: "회의 테이블", w: 4, h: 2, passable: false, color: 0x92633c },
  { kind: "table-side", label: "탁자", w: 2, h: 2, passable: false, color: 0xa3744a },
  { kind: "sofa", label: "소파", w: 3, h: 1, passable: false, color: 0x4663ac },
  { kind: "shelf", label: "책장", w: 4, h: 1, passable: false, color: 0x6b4f35 },
  { kind: "rug", label: "러그", w: 4, h: 3, passable: true, color: 0x3e6f5c },
  { kind: "plant", label: "화분", w: 1, h: 1, passable: false, color: 0x3f8f4f },
];

export function findCatalogEntry(kind: string): CatalogEntry | undefined {
  return FURNITURE_CATALOG.find((e) => e.kind === kind);
}

// 포인터가 가리키는 타일이 footprint 중앙에 오도록 좌상단 타일을 계산
export function anchorTopLeft(pointerTile: { x: number; y: number }, w: number, h: number, rotation: Rotation) {
  const eff = rotation % 2 === 1 ? { w: h, h: w } : { w, h };
  return {
    x: pointerTile.x - Math.floor((eff.w - 1) / 2),
    y: pointerTile.y - Math.floor((eff.h - 1) / 2),
  };
}
