import { TileMap } from "./tile";

// 가구: footprint(타일 단위 w×h)와 통과 가능 여부를 가진다
// 맵 에디터/API 도입 시 서버 데이터로 대체될 모델
export interface Furniture {
  id: string;
  kind: string; // 렌더링 식별용 (table, sofa, rug, plant ...)
  x: number; // 좌상단 타일 x
  y: number; // 좌상단 타일 y
  w: number; // 가로 타일 수
  h: number; // 세로 타일 수
  passable: boolean; // true면 위로 지나갈 수 있다 (러그 등)
  color: number; // 스프라이트 도입 전 임시 렌더링 색
  label?: string; // 캔버스에 표시할 이름
}

// 차단 가구의 footprint를 합집합으로 충돌 그리드에 마킹 (경계 밖은 클리핑)
export function buildCollision(cols: number, rows: number, furniture: Furniture[]): number[][] {
  const grid = Array.from({ length: rows }, () => Array<number>(cols).fill(0));

  for (const f of furniture) {
    if (f.passable) continue;
    const x0 = Math.max(0, f.x);
    const y0 = Math.max(0, f.y);
    const x1 = Math.min(cols, f.x + f.w);
    const y1 = Math.min(rows, f.y + f.h);
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
