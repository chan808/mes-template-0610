"use client";

import { useEffect, useRef, useCallback } from "react";
import { usePresenceStore, CANVAS_WIDTH, CANVAS_HEIGHT, AVATAR_SIZE } from "../stores/presenceStore";
import { ClientMessage } from "../types/ws";

const MOVE_STEP = 5;
const MOVE_SEND_THROTTLE_MS = 100;

const AVATAR_COLORS = [
  "#6366f1", "#ec4899", "#f59e0b", "#10b981",
  "#3b82f6", "#ef4444", "#8b5cf6", "#14b8a6",
];

function getAvatarColor(id: number) {
  return AVATAR_COLORS[Math.abs(id) % AVATAR_COLORS.length];
}

interface SpaceCanvasProps {
  myUserId: number;
  myNickname: string;
  onSend: (msg: ClientMessage) => void;
}

export default function SpaceCanvas({ myUserId, myNickname, onSend }: SpaceCanvasProps) {
  const send = onSend;
  const { presences, agents, myPosition, setMyPosition } = usePresenceStore();

  const pressedKeysRef = useRef<Set<string>>(new Set());
  const lastSendRef = useRef<number>(0);
  const rafRef = useRef<number | null>(null);

  const loop = useCallback(() => {
    const keys = pressedKeysRef.current;
    let { x, y } = usePresenceStore.getState().myPosition;
    let moved = false;

    if (keys.has("ArrowUp") || keys.has("w") || keys.has("W")) { y -= MOVE_STEP; moved = true; }
    if (keys.has("ArrowDown") || keys.has("s") || keys.has("S")) { y += MOVE_STEP; moved = true; }
    if (keys.has("ArrowLeft") || keys.has("a") || keys.has("A")) { x -= MOVE_STEP; moved = true; }
    if (keys.has("ArrowRight") || keys.has("d") || keys.has("D")) { x += MOVE_STEP; moved = true; }

    if (moved) {
      setMyPosition(x, y);
      const now = Date.now();
      if (now - lastSendRef.current >= MOVE_SEND_THROTTLE_MS) {
        const clamped = usePresenceStore.getState().myPosition;
        send({ type: "move", x: Math.round(clamped.x), y: Math.round(clamped.y) });
        lastSendRef.current = now;
      }
    }

    rafRef.current = requestAnimationFrame(loop);
  }, [send, setMyPosition]);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "w", "a", "s", "d", "W", "A", "S", "D"].includes(e.key)) {
        e.preventDefault();
        pressedKeysRef.current.add(e.key);
      }
    };
    const onKeyUp = (e: KeyboardEvent) => pressedKeysRef.current.delete(e.key);

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    rafRef.current = requestAnimationFrame(loop);

    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [loop]);

  const allAvatars = [
    // 내 아바타
    { key: `user-${myUserId}`, x: myPosition.x, y: myPosition.y, nickname: myNickname, colorSeed: myUserId, isMe: true, isAgent: false },
    // 다른 유저 아바타
    ...[...presences.values()]
      .filter((p) => p.userId !== myUserId)
      .map((p) => ({ key: `user-${p.userId}`, x: p.x, y: p.y, nickname: p.nickname, colorSeed: p.avatarId ?? p.userId, isMe: false, isAgent: false })),
    // 에이전트 아바타
    ...[...agents.values()]
      .map((a) => ({ key: `agent-${a.agentId}`, x: a.x, y: a.y, nickname: a.nickname, colorSeed: 0, isMe: false, isAgent: true })),
  ];

  return (
    <div
      className="relative overflow-hidden rounded-xl border border-border bg-slate-900"
      style={{ width: CANVAS_WIDTH, height: CANVAS_HEIGHT, minWidth: CANVAS_WIDTH }}
    >
      {/* 격자 배경 */}
      <div
        className="absolute inset-0 opacity-10"
        style={{
          backgroundImage:
            "linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)",
          backgroundSize: "60px 60px",
        }}
      />

      {/* 경계 표시 */}
      <div className="absolute inset-0 rounded-xl ring-1 ring-inset ring-white/10" />

      {/* 아바타 렌더링 */}
      {allAvatars.map((avatar) => (
        <div
          key={avatar.key}
          className="absolute flex flex-col items-center"
          style={{
            transform: `translate(${avatar.x - AVATAR_SIZE / 2}px, ${avatar.y - AVATAR_SIZE / 2}px)`,
            transition: avatar.isMe ? "none" : "transform 0.1s linear",
            width: AVATAR_SIZE,
          }}
        >
          <div
            className="flex items-center justify-center rounded-full text-white text-sm font-bold shadow-lg"
            style={{
              width: AVATAR_SIZE,
              height: AVATAR_SIZE,
              backgroundColor: avatar.isAgent ? "#7c3aed" : getAvatarColor(avatar.colorSeed),
              boxShadow: avatar.isMe
                ? `0 0 0 2px #fff, 0 0 0 4px ${getAvatarColor(avatar.colorSeed)}`
                : avatar.isAgent
                  ? "0 0 0 2px #fff, 0 0 0 4px #7c3aed, 0 0 12px #7c3aed88"
                  : undefined,
            }}
          >
            {avatar.isAgent ? "AI" : avatar.nickname.charAt(0).toUpperCase()}
          </div>
          <span className="mt-1 max-w-[80px] truncate rounded px-1 text-xs text-white/90 backdrop-blur-sm">
            {avatar.nickname}
            {avatar.isMe && " (나)"}
          </span>
        </div>
      ))}

      {/* 조작 안내 */}
      <div className="absolute bottom-3 right-3 rounded-lg bg-black/50 px-2 py-1 text-xs text-white/60">
        WASD / 방향키로 이동
      </div>
    </div>
  );
}
