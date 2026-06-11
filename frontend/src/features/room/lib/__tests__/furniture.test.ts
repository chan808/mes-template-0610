import { describe, expect, it } from "vitest";
import { buildCollision, createMap, Furniture } from "../furniture";
import { isWalkable } from "../tile";

const f = (over: Partial<Furniture>): Furniture => ({
  id: "f1",
  kind: "table",
  x: 0,
  y: 0,
  w: 1,
  h: 1,
  passable: false,
  color: 0xffffff,
  ...over,
});

describe("buildCollision", () => {
  it("차단_가구_footprint_전체가_이동불가가_된다", () => {
    const grid = buildCollision(10, 10, [f({ x: 2, y: 3, w: 3, h: 2 })]);

    for (let y = 3; y < 5; y++) {
      for (let x = 2; x < 5; x++) {
        expect(grid[y][x]).toBe(1);
      }
    }
    expect(grid[2][2]).toBe(0);
    expect(grid[3][5]).toBe(0);
    expect(grid[5][2]).toBe(0);
  });

  it("통과가능_가구는_충돌맵에_반영되지_않는다", () => {
    const grid = buildCollision(10, 10, [f({ x: 1, y: 1, w: 4, h: 3, passable: true })]);

    expect(grid.flat().every((c) => c === 0)).toBe(true);
  });

  it("맵_경계를_벗어난_footprint는_잘라낸다", () => {
    const grid = buildCollision(5, 5, [f({ x: 3, y: 3, w: 4, h: 4 })]);

    expect(grid[3][3]).toBe(1);
    expect(grid[4][4]).toBe(1);
    expect(grid.length).toBe(5);
    expect(grid[0].length).toBe(5);
  });

  it("겹친_가구는_합집합으로_차단된다", () => {
    const grid = buildCollision(10, 10, [
      f({ id: "a", x: 0, y: 0, w: 2, h: 1 }),
      f({ id: "b", x: 1, y: 0, w: 2, h: 1 }),
    ]);

    expect(grid[0][0]).toBe(1);
    expect(grid[0][1]).toBe(1);
    expect(grid[0][2]).toBe(1);
    expect(grid[0][3]).toBe(0);
  });
});

describe("createMap", () => {
  it("차단_가구_타일은_isWalkable이_false다", () => {
    const map = createMap(10, 10, [f({ x: 4, y: 4, w: 2, h: 2 })]);

    expect(isWalkable(map, 4, 4)).toBe(false);
    expect(isWalkable(map, 5, 5)).toBe(false);
    expect(isWalkable(map, 3, 4)).toBe(true);
    expect(isWalkable(map, 6, 4)).toBe(true);
  });
});
