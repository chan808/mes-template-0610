import { describe, expect, it } from "vitest";
import { canPlace, effectiveSize } from "../furniture";
import { SPAWN_TILE } from "../tile";
import type { PlacedFurniture } from "../../stores/furnitureStore";

const SPEC = { cols: 30, rows: 20 };

const item = (over: Partial<PlacedFurniture>): PlacedFurniture => ({
  id: "f1",
  kind: "table",
  x: 0,
  y: 0,
  w: 2,
  h: 1,
  rotation: 0,
  passable: false,
  color: 0xffffff,
  placedBy: 1,
  ...over,
});

describe("effectiveSize", () => {
  it("홀수_회전이면_가로세로가_뒤바뀐다", () => {
    expect(effectiveSize(3, 2, 0)).toEqual({ w: 3, h: 2 });
    expect(effectiveSize(3, 2, 1)).toEqual({ w: 2, h: 3 });
    expect(effectiveSize(3, 2, 2)).toEqual({ w: 3, h: 2 });
    expect(effectiveSize(3, 2, 3)).toEqual({ w: 2, h: 3 });
  });
});

describe("canPlace", () => {
  it("빈_자리_배치_성공한다", () => {
    expect(canPlace([], SPEC, { x: 5, y: 5, w: 2, h: 1, rotation: 0, passable: false })).toBe(true);
  });

  it("맵_경계를_벗어나면_거부한다", () => {
    expect(canPlace([], SPEC, { x: 29, y: 5, w: 2, h: 1, rotation: 0, passable: false })).toBe(false);
    expect(canPlace([], SPEC, { x: -1, y: 5, w: 2, h: 1, rotation: 0, passable: false })).toBe(false);
    expect(canPlace([], SPEC, { x: 5, y: 19, w: 1, h: 2, rotation: 0, passable: false })).toBe(false);
  });

  it("회전된_footprint로_경계를_검사한다", () => {
    // 2×1을 90도 회전하면 1×2 — y 끝에서 걸린다
    expect(canPlace([], SPEC, { x: 29, y: 5, w: 2, h: 1, rotation: 1, passable: false })).toBe(true);
    expect(canPlace([], SPEC, { x: 29, y: 19, w: 2, h: 1, rotation: 1, passable: false })).toBe(false);
  });

  it("차단_가구끼리_겹치면_거부한다", () => {
    const items = [item({ x: 5, y: 5, w: 2, h: 2 })];

    expect(canPlace(items, SPEC, { x: 6, y: 6, w: 2, h: 1, rotation: 0, passable: false })).toBe(false);
    expect(canPlace(items, SPEC, { x: 7, y: 5, w: 2, h: 1, rotation: 0, passable: false })).toBe(true);
  });

  it("통과_가구는_겹쳐도_허용한다", () => {
    const rug = [item({ passable: true, x: 5, y: 5, w: 4, h: 3 })];

    // 러그 위 차단 가구 OK, 차단 가구 위 러그 OK
    expect(canPlace(rug, SPEC, { x: 6, y: 6, w: 2, h: 1, rotation: 0, passable: false })).toBe(true);
    const table = [item({ x: 5, y: 5, w: 2, h: 2 })];
    expect(canPlace(table, SPEC, { x: 5, y: 5, w: 4, h: 3, rotation: 0, passable: true })).toBe(true);
  });

  it("자기_자신과의_겹침은_무시한다", () => {
    // 한 칸 옆으로 이동 — 기존 위치와 겹치지만 자기 자신이므로 허용
    const items = [item({ id: "moving", x: 5, y: 5, w: 2, h: 2 })];

    expect(canPlace(items, SPEC, { x: 6, y: 5, w: 2, h: 2, rotation: 0, passable: false, excludeId: "moving" })).toBe(true);
  });

  it("차단_가구가_스폰_타일을_덮으면_거부한다", () => {
    expect(
      canPlace([], SPEC, { x: SPAWN_TILE.x, y: SPAWN_TILE.y, w: 1, h: 1, rotation: 0, passable: false }),
    ).toBe(false);
    // 통과 가구는 스폰 위 허용
    expect(
      canPlace([], SPEC, { x: SPAWN_TILE.x, y: SPAWN_TILE.y, w: 1, h: 1, rotation: 0, passable: true }),
    ).toBe(true);
  });
});
