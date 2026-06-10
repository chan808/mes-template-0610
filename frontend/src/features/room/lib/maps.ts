import { MAP_COLS, MAP_ROWS, TileMap } from "./tile";

// 기본 맵: 장애물 없음 (벽·가구는 맵 에디터/게임 모드 도입 시 추가)
export const DEFAULT_MAP: TileMap = {
  cols: MAP_COLS,
  rows: MAP_ROWS,
  collision: Array.from({ length: MAP_ROWS }, () => Array(MAP_COLS).fill(0)),
};
