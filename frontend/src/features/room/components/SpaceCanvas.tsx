"use client";

import type { CSSProperties } from "react";
import { usePresenceStore } from "../stores/presenceStore";
import { useComposerStore } from "../stores/composerStore";
import { useTileMovement } from "../hooks/useTileMovement";
import { CANVAS_HEIGHT, CANVAS_WIDTH, Direction, MOVE_MS, TILE_SIZE, tileToPixel } from "../lib/tile";
import { ClientMessage } from "../types/ws";

const AVATAR_SIZE = TILE_SIZE;

const AVATAR_COLORS = [
  "#6366f1", "#ec4899", "#f59e0b", "#10b981",
  "#3b82f6", "#ef4444", "#8b5cf6", "#14b8a6",
];

function getAvatarColor(id: number) {
  return AVATAR_COLORS[Math.abs(id) % AVATAR_COLORS.length];
}

// 바라보는 방향 표시점의 아바타 내 위치
const DIR_INDICATOR_POS: Record<Direction, CSSProperties> = {
  up: { top: 3, left: "50%", transform: "translateX(-50%)" },
  down: { bottom: 3, left: "50%", transform: "translateX(-50%)" },
  left: { left: 3, top: "50%", transform: "translateY(-50%)" },
  right: { right: 3, top: "50%", transform: "translateY(-50%)" },
};

interface SpaceCanvasProps {
  myUserId: number;
  myNickname: string;
  onSend: (msg: ClientMessage) => void;
}

export default function SpaceCanvas({ myUserId, myNickname, onSend }: SpaceCanvasProps) {
  useTileMovement(onSend);
  const { presences, agents, myTile, myDir } = usePresenceStore();
  const setTarget = useComposerStore((s) => s.setTarget);

  const allAvatars = [
    // 내 아바타
    { key: `user-${myUserId}`, tile: myTile, dir: myDir as Direction | null, nickname: myNickname, colorSeed: myUserId, isMe: true, isAgent: false, userId: myUserId },
    // 다른 유저 아바타
    ...[...presences.values()]
      .filter((p) => p.userId !== myUserId)
      .map((p) => ({ key: `user-${p.userId}`, tile: { x: p.x, y: p.y }, dir: p.dir as Direction | null, nickname: p.nickname, colorSeed: p.avatarId ?? p.userId, isMe: false, isAgent: false, userId: p.userId })),
    // 에이전트 아바타 (방향 없음)
    ...[...agents.values()]
      .map((a) => ({ key: `agent-${a.agentId}`, tile: { x: a.x, y: a.y }, dir: null as Direction | null, nickname: a.nickname, colorSeed: 0, isMe: false, isAgent: true, userId: null as number | null })),
  ];

  // 더블클릭으로 채팅 타겟 설정: 유저 → 귓속말 모드, 에이전트 → @멘션 타겟 (ADR-0002)
  function handleAvatarDoubleClick(avatar: (typeof allAvatars)[number]) {
    if (avatar.isMe) return;
    if (avatar.isAgent) {
      setTarget({ kind: "agent", nickname: avatar.nickname });
    } else if (avatar.userId !== null) {
      setTarget({ kind: "user", userId: avatar.userId, nickname: avatar.nickname });
    }
  }

  return (
    <div
      className="relative overflow-hidden rounded-xl border border-border bg-slate-900"
      style={{ width: CANVAS_WIDTH, height: CANVAS_HEIGHT, minWidth: CANVAS_WIDTH }}
    >
      {/* 타일 격자 배경 */}
      <div
        className="absolute inset-0 opacity-10"
        style={{
          backgroundImage:
            "linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)",
          backgroundSize: `${TILE_SIZE}px ${TILE_SIZE}px`,
        }}
      />

      {/* 경계 표시 */}
      <div className="absolute inset-0 rounded-xl ring-1 ring-inset ring-white/10" />

      {/* 아바타 렌더링: 타일 좌표 → 픽셀 변환 후 한 칸 시간만큼 보간 */}
      {allAvatars.map((avatar) => {
        const { px, py } = tileToPixel(avatar.tile);
        return (
          <div
            key={avatar.key}
            className={`absolute flex flex-col items-center ${avatar.isMe ? "" : "cursor-pointer"}`}
            onDoubleClick={() => handleAvatarDoubleClick(avatar)}
            title={avatar.isMe ? undefined : avatar.isAgent ? "더블클릭: 이 에이전트에게만 말하기" : "더블클릭: 귓속말"}
            style={{
              transform: `translate(${px - AVATAR_SIZE / 2}px, ${py - AVATAR_SIZE / 2}px)`,
              transition: `transform ${MOVE_MS}ms linear`,
              width: AVATAR_SIZE,
            }}
          >
            <div
              className="relative flex items-center justify-center rounded-full text-white text-sm font-bold shadow-lg"
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
              {avatar.dir && (
                <span
                  className="absolute h-1.5 w-1.5 rounded-full bg-white/90"
                  style={DIR_INDICATOR_POS[avatar.dir]}
                />
              )}
            </div>
            <span className="mt-1 max-w-[80px] truncate rounded px-1 text-xs text-white/90 backdrop-blur-sm">
              {avatar.nickname}
              {avatar.isMe && " (나)"}
            </span>
          </div>
        );
      })}

      {/* 조작 안내 */}
      <div className="absolute bottom-3 right-3 rounded-lg bg-black/50 px-2 py-1 text-xs text-white/60">
        WASD / 방향키로 이동
      </div>
    </div>
  );
}
