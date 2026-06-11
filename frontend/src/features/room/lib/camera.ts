// 카메라 오프셋 계산: 월드 컨테이너에 적용할 translate 값
// 포커스(픽셀 좌표)를 뷰포트 중앙에 두되, 월드 밖 빈 공간이 보이지 않게 클램프.
// 월드가 뷰포트보다 작은 축은 중앙 정렬한다.
export function cameraOffset(
  viewportW: number,
  viewportH: number,
  worldW: number,
  worldH: number,
  focusX: number,
  focusY: number,
): { x: number; y: number } {
  return {
    x: axisOffset(viewportW, worldW, focusX),
    y: axisOffset(viewportH, worldH, focusY),
  };
}

function axisOffset(viewport: number, world: number, focus: number): number {
  if (world <= viewport) return (viewport - world) / 2;
  const offset = viewport / 2 - focus;
  return Math.min(0, Math.max(viewport - world, offset));
}
