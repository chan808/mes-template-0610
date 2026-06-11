import { beforeEach, describe, expect, it } from "vitest";
import { canEditFurniture, useFurnitureStore } from "../furnitureStore";
import type { PlacedFurniture } from "../furnitureStore";

const OWNER = 100;
const MEMBER_A = 1;
const MEMBER_B = 2;
const SPEC = { cols: 30, rows: 20 };

const table = (over: Partial<PlacedFurniture>): PlacedFurniture => ({
  id: "t1",
  kind: "table",
  x: 5,
  y: 5,
  w: 2,
  h: 1,
  rotation: 0,
  passable: false,
  color: 0xffffff,
  placedBy: MEMBER_A,
  ...over,
});

beforeEach(() => {
  useFurnitureStore.getState().reset();
});

describe("canEditFurniture", () => {
  it("방장은_모든_가구를_편집할_수_있다", () => {
    expect(canEditFurniture(table({}), OWNER, OWNER)).toBe(true);
    expect(canEditFurniture(table({ placedBy: null }), OWNER, OWNER)).toBe(true);
  });

  it("멤버는_본인이_배치한_가구만_편집할_수_있다", () => {
    expect(canEditFurniture(table({ placedBy: MEMBER_A }), MEMBER_A, OWNER)).toBe(true);
    expect(canEditFurniture(table({ placedBy: MEMBER_A }), MEMBER_B, OWNER)).toBe(false);
  });

  it("기본_가구는_방장만_편집할_수_있다", () => {
    expect(canEditFurniture(table({ placedBy: null }), MEMBER_A, OWNER)).toBe(false);
  });
});

describe("furnitureStore.place", () => {
  it("빈_자리_배치가_성공하고_items에_추가된다", () => {
    const ok = useFurnitureStore.getState().place(table({}), SPEC);

    expect(ok).toBe(true);
    expect(useFurnitureStore.getState().items.get("t1")?.x).toBe(5);
  });

  it("차단_가구가_겹치면_배치를_거부한다", () => {
    const s = useFurnitureStore.getState();
    s.place(table({}), SPEC);

    const ok = s.place(table({ id: "t2", x: 6, y: 5 }), SPEC);

    expect(ok).toBe(false);
    expect(useFurnitureStore.getState().items.has("t2")).toBe(false);
  });
});

describe("furnitureStore.move", () => {
  it("본인_가구_이동이_성공하고_회전도_반영된다", () => {
    const s = useFurnitureStore.getState();
    s.place(table({}), SPEC);

    const ok = s.move("t1", { x: 8, y: 8, rotation: 1 }, MEMBER_A, OWNER, SPEC);

    expect(ok).toBe(true);
    const moved = useFurnitureStore.getState().items.get("t1")!;
    expect([moved.x, moved.y, moved.rotation]).toEqual([8, 8, 1]);
  });

  it("타인_가구_이동은_거부한다", () => {
    const s = useFurnitureStore.getState();
    s.place(table({}), SPEC);

    const ok = s.move("t1", { x: 8, y: 8, rotation: 0 }, MEMBER_B, OWNER, SPEC);

    expect(ok).toBe(false);
    expect(useFurnitureStore.getState().items.get("t1")!.x).toBe(5);
  });

  it("제자리_근처_이동시_자기_footprint와의_겹침은_허용한다", () => {
    const s = useFurnitureStore.getState();
    s.place(table({}), SPEC);

    const ok = s.move("t1", { x: 6, y: 5, rotation: 0 }, MEMBER_A, OWNER, SPEC);

    expect(ok).toBe(true);
  });
});

describe("furnitureStore.remove", () => {
  it("방장은_타인_가구를_삭제할_수_있다", () => {
    const s = useFurnitureStore.getState();
    s.place(table({}), SPEC);

    expect(s.remove("t1", OWNER, OWNER)).toBe(true);
    expect(useFurnitureStore.getState().items.has("t1")).toBe(false);
  });

  it("멤버는_타인_가구를_삭제할_수_없다", () => {
    const s = useFurnitureStore.getState();
    s.place(table({}), SPEC);

    expect(s.remove("t1", MEMBER_B, OWNER)).toBe(false);
    expect(useFurnitureStore.getState().items.has("t1")).toBe(true);
  });
});

describe("furnitureStore.seed", () => {
  it("시드는_한_번만_적용된다", () => {
    const s = useFurnitureStore.getState();
    s.seed([table({})]);
    s.seed([table({ id: "dup", x: 10, y: 10 })]);

    const items = useFurnitureStore.getState().items;
    expect(items.size).toBe(1);
    expect(items.has("t1")).toBe(true);
  });
});
