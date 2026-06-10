"use client";

import { useEffect, useRef } from "react";
import { usePresenceStore } from "../stores/presenceStore";
import { DEFAULT_MAP } from "../lib/maps";
import { DIR_DELTA, Direction, MOVE_MS, isWalkable } from "../lib/tile";
import { ClientMessage } from "../types/ws";

// 물리 키 → 방향 매핑 (WASD + 방향키)
const KEY_TO_DIR: Record<string, Direction> = {
  arrowup: "up",
  arrowdown: "down",
  arrowleft: "left",
  arrowright: "right",
  w: "up",
  s: "down",
  a: "left",
  d: "right",
};

// 칸 단위 이동: 이동 중에는 입력을 받지 않고, 한 칸 이동이 끝나면
// 눌려 있는 키(마지막 입력 우선)를 재평가해 연속 이동한다 (Zep 방식)
export function useTileMovement(send: (msg: ClientMessage) => void) {
  const pressedRef = useRef<string[]>([]); // 누른 순서 유지 — 마지막 키가 우선
  const movingUntilRef = useRef(0);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    const step = (dir: Direction, now: number) => {
      const { myTile, myDir, setMyTile } = usePresenceStore.getState();
      const delta = DIR_DELTA[dir];
      const nx = myTile.x + delta.x;
      const ny = myTile.y + delta.y;

      // 막힌 방향이면 바라보는 방향만 변경 (이동·전송 없음)
      if (!isWalkable(DEFAULT_MAP, nx, ny)) {
        if (myDir !== dir) setMyTile(myTile.x, myTile.y, dir);
        return;
      }

      setMyTile(nx, ny, dir);
      send({ type: "move", x: nx, y: ny, dir });
      movingUntilRef.current = now + MOVE_MS;
    };

    const loop = (now: number) => {
      if (now >= movingUntilRef.current) {
        const keys = pressedRef.current;
        const lastKey = keys[keys.length - 1];
        if (lastKey) step(KEY_TO_DIR[lastKey], now);
      }
      rafRef.current = requestAnimationFrame(loop);
    };

    const normalizeKey = (e: KeyboardEvent) => e.key.toLowerCase();

    // 채팅 입력 등 폼 요소에 포커스가 있으면 이동 키를 가로채지 않는다
    const isTypingTarget = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (isTypingTarget(e)) return;
      const key = normalizeKey(e);
      if (!(key in KEY_TO_DIR)) return;
      e.preventDefault();
      if (!pressedRef.current.includes(key)) {
        pressedRef.current.push(key);
      }
    };

    const onKeyUp = (e: KeyboardEvent) => {
      const key = normalizeKey(e);
      pressedRef.current = pressedRef.current.filter((k) => k !== key);
    };

    // 탭 전환 등으로 keyup을 놓치면 키가 눌린 상태로 남으므로 초기화
    const onBlur = () => {
      pressedRef.current = [];
    };

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    window.addEventListener("blur", onBlur);
    rafRef.current = requestAnimationFrame(loop);

    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("blur", onBlur);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [send]);
}
