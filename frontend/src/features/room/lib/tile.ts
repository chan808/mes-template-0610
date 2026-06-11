// 타일 기반 이동 좌표계 — 서버(realtime/game/movement.go)와 상수를 일치시킬 것
export const TILE_SIZE = 40;
export const MAP_COLS = 30;
export const MAP_ROWS = 20;
export const CANVAS_WIDTH = MAP_COLS * TILE_SIZE;
export const CANVAS_HEIGHT = MAP_ROWS * TILE_SIZE;

// 한 칸 이동 시간 — 서버 토큰 버킷 회복 주기(140ms)보다 길어야 정상 이동이 차단되지 않는다
export const MOVE_MS = 160;

// 입장 스폰 타일 — 서버 game.SpawnTileX/Y와 일치시킬 것
export const SPAWN_TILE = { x: 15, y: 10 };

export type Direction = "up" | "down" | "left" | "right";

export interface TilePos {
  x: number;
  y: number;
}

export const DIR_DELTA: Record<Direction, TilePos> = {
  up: { x: 0, y: -1 },
  down: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
};

// 맵 크기 명세 — 구독 등급별 맵 크기 확장의 단위
export interface MapSpec {
  cols: number;
  rows: number;
}

// 충돌 맵: collision[y][x] === 1 이면 이동 불가
export interface TileMap {
  cols: number;
  rows: number;
  collision: number[][];
}

// 타일 좌표 → 타일 중심 픽셀 좌표
export function tileToPixel(tile: TilePos): { px: number; py: number } {
  return {
    px: tile.x * TILE_SIZE + TILE_SIZE / 2,
    py: tile.y * TILE_SIZE + TILE_SIZE / 2,
  };
}

export function isWalkable(map: TileMap, x: number, y: number): boolean {
  if (x < 0 || x >= map.cols || y < 0 || y >= map.rows) return false;
  return map.collision[y][x] === 0;
}
