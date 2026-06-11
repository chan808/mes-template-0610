import { describe, expect, it } from "vitest";
import { TileWalker } from "../tileWalker";
import { MOVE_MS } from "../tile";

describe("TileWalker", () => {
  it("push_한칸은_MOVE_MS에_걸쳐_보간되어_도착한다", () => {
    const w = new TileWalker({ x: 5, y: 5 });

    w.push({ x: 6, y: 5 }, "right");
    expect(w.moving).toBe(true);

    w.update(MOVE_MS / 2);
    expect(w.pos.x).toBeCloseTo(5.5);
    expect(w.pos.y).toBe(5);

    w.update(MOVE_MS / 2);
    expect(w.pos.x).toBe(6);
    expect(w.moving).toBe(false);
    expect(w.dir).toBe("right");
  });

  it("연속_push는_큐로_재생되어_타일_중심을_경유한다", () => {
    const w = new TileWalker({ x: 0, y: 0 });

    // 오른쪽 → 아래: 코너를 대각선으로 가로지르면 안 된다
    w.push({ x: 1, y: 0 });
    w.push({ x: 1, y: 1 });

    w.update(MOVE_MS / 2); // 첫 세그먼트 중간: 아직 y는 0이어야 한다
    expect(w.pos.y).toBe(0);
    expect(w.pos.x).toBeGreaterThan(0);
  });

  it("세그먼트_경계에서_남은_시간이_다음_세그먼트로_이월된다", () => {
    const w = new TileWalker({ x: 0, y: 0 });

    w.push({ x: 1, y: 0 });
    w.push({ x: 2, y: 0 });

    w.update(MOVE_MS * 1.5);
    expect(w.pos.x).toBeCloseTo(1.5);

    w.update(MOVE_MS * 0.5);
    expect(w.pos.x).toBe(2);
    expect(w.moving).toBe(false);
  });

  it("큐가_밀리면_가속해서_따라잡는다", () => {
    const slow = new TileWalker({ x: 0, y: 0 });
    slow.push({ x: 1, y: 0 });

    const fast = new TileWalker({ x: 0, y: 0 });
    fast.push({ x: 1, y: 0 });
    fast.push({ x: 2, y: 0 });
    fast.push({ x: 3, y: 0 });

    slow.update(MOVE_MS / 2);
    fast.update(MOVE_MS / 2);

    // 같은 시간 동안 큐가 긴 쪽이 더 멀리 진행한다
    expect(fast.pos.x).toBeGreaterThan(slow.pos.x);
  });

  it("비인접_push는_큐를_비우고_즉시_스냅한다", () => {
    const w = new TileWalker({ x: 0, y: 0 });

    w.push({ x: 1, y: 0 });
    w.push({ x: 10, y: 10 }, "down");

    expect(w.pos).toEqual({ x: 10, y: 10 });
    expect(w.dir).toBe("down");
    expect(w.moving).toBe(false);
  });

  it("같은_타일_push는_무시하고_방향만_갱신한다", () => {
    const w = new TileWalker({ x: 3, y: 3 }, "down");

    w.push({ x: 3, y: 3 }, "left");

    expect(w.moving).toBe(false);
    expect(w.pos).toEqual({ x: 3, y: 3 });
    expect(w.dir).toBe("left");
  });

  it("이동_방향에_따라_dir이_갱신된다", () => {
    const w = new TileWalker({ x: 5, y: 5 }, "down");

    w.push({ x: 5, y: 4 });
    w.update(1);

    expect(w.dir).toBe("up");
  });
});
