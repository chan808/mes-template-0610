import { describe, expect, it } from "vitest";
import { SEED_FURNITURE, resolveMapSpec } from "../maps";
import { createMap } from "../furniture";
import { isWalkable, MAP_COLS, MAP_ROWS, SPAWN_TILE } from "../tile";

// 에이전트 고정 슬롯 — docs/architecture.md와 일치
const AGENT_SLOTS = [
  { x: 22, y: 5 },
  { x: 17, y: 5 },
  { x: 22, y: 12 },
  { x: 17, y: 12 },
];

describe("resolveMapSpec", () => {
  it("기본_맵_크기는_서버_검증_범위와_일치한다", () => {
    const spec = resolveMapSpec();

    expect(spec.cols).toBe(MAP_COLS);
    expect(spec.rows).toBe(MAP_ROWS);
  });
});

describe("SEED_FURNITURE", () => {
  it("스폰_타일과_에이전트_슬롯은_가구에_막히지_않는다", () => {
    const spec = resolveMapSpec();
    const map = createMap(spec.cols, spec.rows, SEED_FURNITURE);

    expect(isWalkable(map, SPAWN_TILE.x, SPAWN_TILE.y)).toBe(true);
    for (const slot of AGENT_SLOTS) {
      expect(isWalkable(map, slot.x, slot.y)).toBe(true);
    }
  });

  it("차단_가구와_통과_가구가_모두_존재한다", () => {
    expect(SEED_FURNITURE.some((f) => !f.passable)).toBe(true);
    expect(SEED_FURNITURE.some((f) => f.passable)).toBe(true);
  });

  it("기본_가구는_placedBy가_없어_방장만_편집_가능하다", () => {
    expect(SEED_FURNITURE.every((f) => f.placedBy === null)).toBe(true);
  });

  it("스폰_타일에서_최소_한_방향은_이동_가능하다", () => {
    const spec = resolveMapSpec();
    const map = createMap(spec.cols, spec.rows, SEED_FURNITURE);
    const neighbors = [
      [SPAWN_TILE.x + 1, SPAWN_TILE.y],
      [SPAWN_TILE.x - 1, SPAWN_TILE.y],
      [SPAWN_TILE.x, SPAWN_TILE.y + 1],
      [SPAWN_TILE.x, SPAWN_TILE.y - 1],
    ];

    expect(neighbors.some(([x, y]) => isWalkable(map, x, y))).toBe(true);
  });
});
