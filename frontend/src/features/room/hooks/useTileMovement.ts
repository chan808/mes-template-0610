"use client";

import { useEffect, useRef } from "react";
import { usePresenceStore } from "../stores/presenceStore";
import { DIR_DELTA, Direction, MOVE_MS, TileMap, isWalkable } from "../lib/tile";
import { ClientMessage } from "../types/ws";

// 물리 키 코드 → 방향 매핑 (WASD + 방향키)
// e.key 대신 e.code 사용 — 한글 IME 상태에서도 WASD가 동작해야 한다 ("w" → "ㅈ" 문제)
const CODE_TO_DIR: Record<string, Direction> = {
  ArrowUp: "up",
  ArrowDown: "down",
  ArrowLeft: "left",
  ArrowRight: "right",
  KeyW: "up",
  KeyS: "down",
  KeyA: "left",
  KeyD: "right",
};

// 칸 단위 이동: 이동 중에는 입력을 받지 않고, 한 칸 이동이 끝나면
// 눌려 있는 키(마지막 입력 우선)를 재평가해 연속 이동한다 (Zep 방식)
// map: 가구 충돌이 반영된 방의 충돌맵
export function useTileMovement(send: (msg: ClientMessage) => void, map: TileMap) {
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
      if (!isWalkable(map, nx, ny)) {
        if (myDir !== dir) setMyTile(myTile.x, myTile.y, dir);
        return;
      }

      setMyTile(nx, ny, dir);
      send({ type: "move", x: nx, y: ny, dir });
      movingUntilRef.current = now + MOVE_MS;
    };

    const loop = (now: number) => {
      if (now >= movingUntilRef.current) {
        const codes = pressedRef.current;
        const lastCode = codes[codes.length - 1];
        if (lastCode) step(CODE_TO_DIR[lastCode], now);
      }
      rafRef.current = requestAnimationFrame(loop);
    };

    // 채팅 입력 등 폼 요소에 포커스가 있으면 이동 키를 가로채지 않는다
    const isTypingTarget = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
    };

    const onKeyDown = (e: KeyboardEvent) => {
      if (isTypingTarget(e)) return;
      if (!(e.code in CODE_TO_DIR)) return;
      e.preventDefault();
      if (!pressedRef.current.includes(e.code)) {
        pressedRef.current.push(e.code);
      }
    };

    const onKeyUp = (e: KeyboardEvent) => {
      pressedRef.current = pressedRef.current.filter((k) => k !== e.code);
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
  }, [send, map]);
}
