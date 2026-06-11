import { describe, expect, it } from "vitest";
import { cameraOffset } from "../camera";

describe("cameraOffset", () => {
  it("월드가_뷰포트보다_크면_포커스를_화면_중앙에_둔다", () => {
    const { x, y } = cameraOffset(800, 600, 2000, 1500, 1000, 750);

    // world 좌표 1000,750이 화면 400,300에 오도록 오프셋
    expect(x).toBe(400 - 1000);
    expect(y).toBe(300 - 750);
  });

  it("월드_가장자리에서는_빈_공간이_보이지_않게_클램프한다", () => {
    // 포커스가 좌상단 구석
    expect(cameraOffset(800, 600, 2000, 1500, 0, 0)).toEqual({ x: 0, y: 0 });
    // 포커스가 우하단 구석
    expect(cameraOffset(800, 600, 2000, 1500, 2000, 1500)).toEqual({ x: 800 - 2000, y: 600 - 1500 });
  });

  it("월드가_뷰포트보다_작으면_중앙_정렬한다", () => {
    const { x, y } = cameraOffset(800, 600, 400, 300, 200, 150);

    expect(x).toBe(200);
    expect(y).toBe(150);
  });

  it("축별로_독립_적용된다", () => {
    // 가로는 월드가 작고 세로는 크다
    const { x, y } = cameraOffset(800, 600, 400, 1500, 200, 750);

    expect(x).toBe(200); // 중앙 정렬
    expect(y).toBe(300 - 750); // 포커스 추적
  });
});
