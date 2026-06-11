import { MapSpec, SPAWN_TILE, TileMap } from "./tile";

// 90도 단위 회전 (0=원본, 1=90°, 2=180°, 3=270°)
export type Rotation = 0 | 1 | 2 | 3;

// 가구: footprint(타일 단위 w×h)와 통과 가능 여부를 가진다
// 맵 에디터/API 도입 시 서버 데이터로 대체될 모델
export interface Furniture {
  id: string;
  kind: string; // 렌더링 식별용 (table, sofa, rug, plant ...)
  x: number; // 좌상단 타일 x
  y: number; // 좌상단 타일 y
  w: number; // 가로 타일 수 (회전 전 원본)
  h: number; // 세로 타일 수 (회전 전 원본)
  rotation?: Rotation;
  passable: boolean; // true면 위로 지나갈 수 있다 (러그 등)
  color: number; // 스프라이트 도입 전 임시 렌더링 색
  label?: string; // 캔버스에 표시할 이름
}

// 회전을 반영한 실제 footprint 크기
export function effectiveSize(w: number, h: number, rotation: Rotation): { w: number; h: number } {
  return rotation % 2 === 1 ? { w: h, h: w } : { w, h };
}

// 차단 가구의 footprint를 합집합으로 충돌 그리드에 마킹 (경계 밖은 클리핑)
export function buildCollision(cols: number, rows: number, furniture: Furniture[]): number[][] {
  const grid = Array.from({ length: rows }, () => Array<number>(cols).fill(0));

  for (const f of furniture) {
    if (f.passable) continue;
    const size = effectiveSize(f.w, f.h, f.rotation ?? 0);
    const x0 = Math.max(0, f.x);
    const y0 = Math.max(0, f.y);
    const x1 = Math.min(cols, f.x + size.w);
    const y1 = Math.min(rows, f.y + size.h);
    for (let y = y0; y < y1; y++) {
      for (let x = x0; x < x1; x++) {
        grid[y][x] = 1;
      }
    }
  }
  return grid;
}

export function createMap(cols: number, rows: number, furniture: Furniture[]): TileMap {
  return { cols, rows, collision: buildCollision(cols, rows, furniture) };
}

export interface PlacementCandidate {
  x: number;
  y: number;
  w: number; // 회전 전 원본 크기
  h: number;
  rotation: Rotation;
  passable: boolean;
  excludeId?: string; // 이동 중인 가구 자신은 겹침 검사에서 제외
}

// 배치 가능 여부: 맵 범위 + 차단 가구끼리 겹침 금지 + 차단 가구의 스폰 타일 점유 금지.
// 통과 가구(러그)는 무엇과도 겹칠 수 있다. 서버 검증 도입 시 동일 규칙을 서버에도 적용할 것
export function canPlace(items: Furniture[], spec: MapSpec, c: PlacementCandidate): boolean {
  const size = effectiveSize(c.w, c.h, c.rotation);

  if (c.x < 0 || c.y < 0 || c.x + size.w > spec.cols || c.y + size.h > spec.rows) return false;

  if (!c.passable) {
    // 신규 입장자가 가구 안에 스폰되지 않도록 차단
    if (
      SPAWN_TILE.x >= c.x && SPAWN_TILE.x < c.x + size.w &&
      SPAWN_TILE.y >= c.y && SPAWN_TILE.y < c.y + size.h
    ) {
      return false;
    }

    for (const f of items) {
      if (f.passable || f.id === c.excludeId) continue;
      const fs = effectiveSize(f.w, f.h, f.rotation ?? 0);
      const overlap =
        c.x < f.x + fs.w && c.x + size.w > f.x &&
        c.y < f.y + fs.h && c.y + size.h > f.y;
      if (overlap) return false;
    }
  }
  return true;
}
