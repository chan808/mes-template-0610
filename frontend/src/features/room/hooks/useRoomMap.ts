"use client";

import { useEffect, useMemo } from "react";
import { useFurnitureStore } from "../stores/furnitureStore";
import { SEED_FURNITURE, resolveMapSpec } from "../lib/maps";
import { createMap } from "../lib/furniture";

// 방 맵 데이터: 가구 스토어에서 충돌맵을 파생한다 — 가구 배치/이동/삭제가 이동 충돌에 즉시 반영
export function useRoomMap() {
  const items = useFurnitureStore((s) => s.items);
  const seed = useFurnitureStore((s) => s.seed);

  // 기본 레이아웃 1회 주입 (서버 맵 데이터 도입 시 입장 응답으로 대체)
  useEffect(() => {
    seed(SEED_FURNITURE);
  }, [seed]);

  const spec = useMemo(() => resolveMapSpec(), []);
  const map = useMemo(() => createMap(spec.cols, spec.rows, [...items.values()]), [spec, items]);

  return { spec, map };
}
