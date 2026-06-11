"use client";

import { useEffect, useMemo, useRef } from "react";
import type { Application, Container, Graphics, Ticker } from "pixi.js";
import { usePresenceStore } from "../stores/presenceStore";
import { useComposerStore } from "../stores/composerStore";
import { useTileMovement } from "../hooks/useTileMovement";
import { Direction, TILE_SIZE, TilePos, tileToPixel } from "../lib/tile";
import { TileWalker } from "../lib/tileWalker";
import { cameraOffset } from "../lib/camera";
import { getRoomMap } from "../lib/maps";
import { Furniture } from "../lib/furniture";
import { ClientMessage } from "../types/ws";

const BODY_RADIUS = 16;
const DOUBLE_TAP_MS = 350;

const AVATAR_COLORS = [
  0x6366f1, 0xec4899, 0xf59e0b, 0x10b981,
  0x3b82f6, 0xef4444, 0x8b5cf6, 0x14b8a6,
];

function getAvatarColor(id: number) {
  return AVATAR_COLORS[Math.abs(id) % AVATAR_COLORS.length];
}

// 방향 표시점의 본체 중심 기준 오프셋
const DOT_OFFSET: Record<Direction, { x: number; y: number }> = {
  up: { x: 0, y: -11 },
  down: { x: 0, y: 11 },
  left: { x: -11, y: 0 },
  right: { x: 11, y: 0 },
};

// 렌더러가 추적하는 아바타 1개의 상태
interface AvatarNode {
  root: Container;
  dot: Graphics;
  walker: TileWalker;
  isAgent: boolean;
  userId: number | null;
  nickname: string;
  lastTap: number;
}

// 스토어 → 렌더러 동기화용 목표 상태
interface AvatarTarget {
  key: string;
  tile: TilePos;
  dir: Direction | null;
  nickname: string;
  colorSeed: number;
  isAgent: boolean;
  isMe: boolean;
  userId: number | null;
}

interface SpaceCanvasProps {
  myUserId: number;
  myNickname: string;
  onSend: (msg: ClientMessage) => void;
}

export default function SpaceCanvas({ myUserId, myNickname, onSend }: SpaceCanvasProps) {
  // 가구 충돌이 반영된 방 맵 (맵 에디터/API 도입 전까지 정적 데이터)
  const roomMap = useMemo(() => getRoomMap(), []);
  useTileMovement(onSend, roomMap.map);

  const hostRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    let disposed = false;
    let app: Application | undefined;
    let unsubscribe: (() => void) | undefined;

    (async () => {
      // SSR 번들에서 제외하고 클라이언트에서만 로드
      const PIXI = await import("pixi.js");
      if (disposed) return;

      const instance = new PIXI.Application();
      await instance.init({
        background: 0x0f172a,
        resizeTo: host,
        antialias: true,
        resolution: typeof window === "undefined" ? 1 : window.devicePixelRatio,
        autoDensity: true,
      });
      if (disposed) {
        instance.destroy(true, { children: true });
        return;
      }
      app = instance;
      host.appendChild(instance.canvas);

      const { map, furniture } = roomMap;
      const worldW = map.cols * TILE_SIZE;
      const worldH = map.rows * TILE_SIZE;

      // 레이어 구성: 바닥 → 통과 가구 → (차단 가구 + 아바타, y 정렬)
      const world = new PIXI.Container();
      const entityLayer = new PIXI.Container();
      entityLayer.sortableChildren = true;

      world.addChild(buildFloor(PIXI, map.cols, map.rows));
      for (const f of furniture) {
        const node = buildFurniture(PIXI, f);
        if (f.passable) {
          world.addChild(node);
        } else {
          // 발밑(y)이 더 아래인 쪽이 앞에 보이도록 타일 단위 zIndex
          node.zIndex = f.y + f.h;
          entityLayer.addChild(node);
        }
      }
      world.addChild(entityLayer);
      instance.stage.addChild(world);

      const nodes = new Map<string, AvatarNode>();
      const meKey = `user-${myUserId}`;

      // 더블탭(클릭)으로 채팅 타겟 설정: 유저 → 귓속말, 에이전트 → @멘션 (ADR-0002)
      const onAvatarTap = (node: AvatarNode) => {
        const now = performance.now();
        const isDouble = now - node.lastTap < DOUBLE_TAP_MS;
        node.lastTap = now;
        if (!isDouble) return;
        if (node.isAgent) {
          useComposerStore.getState().setTarget({ kind: "agent", nickname: node.nickname });
        } else if (node.userId !== null) {
          useComposerStore.getState().setTarget({ kind: "user", userId: node.userId, nickname: node.nickname });
        }
      };

      const createNode = (t: AvatarTarget): AvatarNode => {
        const root = new PIXI.Container();
        const color = t.isAgent ? 0x7c3aed : getAvatarColor(t.colorSeed);

        const body = new PIXI.Graphics();
        body.circle(0, 0, BODY_RADIUS).fill(color);
        if (t.isMe) body.circle(0, 0, BODY_RADIUS + 2.5).stroke({ width: 2, color: 0xffffff });
        if (t.isAgent) body.circle(0, 0, BODY_RADIUS + 2.5).stroke({ width: 2, color: 0xc4b5fd });
        root.addChild(body);

        const initial = new PIXI.Text({
          text: t.isAgent ? "AI" : t.nickname.charAt(0).toUpperCase(),
          style: { fontSize: 13, fontWeight: "700", fill: 0xffffff },
        });
        initial.anchor.set(0.5);
        root.addChild(initial);

        const dot = new PIXI.Graphics();
        dot.circle(0, 0, 3).fill({ color: 0xffffff, alpha: 0.9 });
        dot.visible = !t.isAgent;
        root.addChild(dot);

        const name = new PIXI.Text({
          text: t.isMe ? `${t.nickname} (나)` : t.nickname,
          style: { fontSize: 11, fill: 0xffffff, fontWeight: "500" },
        });
        name.anchor.set(0.5, 0);
        name.y = BODY_RADIUS + 5;
        name.alpha = 0.92;
        root.addChild(name);

        const node: AvatarNode = {
          root,
          dot,
          walker: new TileWalker(t.tile, t.dir ?? "down"),
          isAgent: t.isAgent,
          userId: t.userId,
          nickname: t.nickname,
          lastTap: 0,
        };

        if (!t.isMe) {
          root.eventMode = "static";
          root.cursor = "pointer";
          root.on("pointertap", () => onAvatarTap(node));
        }

        entityLayer.addChild(root);
        return node;
      };

      // 스토어 상태를 렌더 노드에 반영: 생성·이동 큐 적재·제거
      const sync = (state: ReturnType<typeof usePresenceStore.getState>) => {
        const targets = new Map<string, AvatarTarget>();
        targets.set(meKey, {
          key: meKey, tile: state.myTile, dir: state.myDir, nickname: myNickname,
          colorSeed: myUserId, isAgent: false, isMe: true, userId: myUserId,
        });
        for (const p of state.presences.values()) {
          if (p.userId === myUserId) continue;
          targets.set(`user-${p.userId}`, {
            key: `user-${p.userId}`, tile: { x: p.x, y: p.y }, dir: p.dir, nickname: p.nickname,
            colorSeed: p.avatarId ?? p.userId, isAgent: false, isMe: false, userId: p.userId,
          });
        }
        for (const a of state.agents.values()) {
          targets.set(`agent-${a.agentId}`, {
            key: `agent-${a.agentId}`, tile: { x: a.x, y: a.y }, dir: null, nickname: a.nickname,
            colorSeed: 0, isAgent: true, isMe: false, userId: null,
          });
        }

        for (const [key, t] of targets) {
          const node = nodes.get(key);
          if (!node) {
            nodes.set(key, createNode(t));
          } else {
            // 인접 칸은 큐 재생, 비인접(스냅 보정·순간이동)은 TileWalker가 즉시 스냅
            node.walker.push(t.tile, t.dir ?? undefined);
          }
        }
        for (const [key, node] of nodes) {
          if (!targets.has(key)) {
            node.root.destroy({ children: true });
            nodes.delete(key);
          }
        }
      };

      // 매 프레임: 이동 큐 재생 → 픽셀 좌표 반영 → 카메라 추적
      const tick = (ticker: Ticker) => {
        const dt = ticker.deltaMS;
        for (const node of nodes.values()) {
          node.walker.update(dt);
          const { px, py } = tileToPixel(node.walker.pos);
          node.root.position.set(px, py);
          node.root.zIndex = node.walker.pos.y + 0.5;
          const offset = DOT_OFFSET[node.walker.dir];
          node.dot.position.set(offset.x, offset.y);
        }

        const me = nodes.get(meKey);
        if (me) {
          const { px, py } = tileToPixel(me.walker.pos);
          const cam = cameraOffset(instance.screen.width, instance.screen.height, worldW, worldH, px, py);
          world.position.set(Math.round(cam.x), Math.round(cam.y));
        }
      };

      sync(usePresenceStore.getState());
      unsubscribe = usePresenceStore.subscribe(sync);
      instance.ticker.add(tick);
    })();

    return () => {
      disposed = true;
      unsubscribe?.();
      if (app) {
        app.destroy(true, { children: true });
        app = undefined;
      }
    };
  }, [roomMap, myUserId, myNickname]);

  return (
    <div className="relative h-full w-full overflow-hidden rounded-xl border border-border bg-slate-900">
      <div ref={hostRef} className="absolute inset-0" />
      {/* 조작 안내 오버레이 (React/DOM 레이어) */}
      <div className="pointer-events-none absolute bottom-3 right-3 rounded-lg bg-black/50 px-2 py-1 text-xs text-white/60">
        WASD / 방향키 이동 · 아바타 더블클릭: 귓속말/에이전트 지정
      </div>
    </div>
  );
}

// 바닥: 배경 + 타일 격자 + 외곽선
function buildFloor(PIXI: typeof import("pixi.js"), cols: number, rows: number): Graphics {
  const g = new PIXI.Graphics();
  const w = cols * TILE_SIZE;
  const h = rows * TILE_SIZE;

  g.rect(0, 0, w, h).fill(0x1a2540);
  for (let x = 0; x <= cols; x++) {
    g.moveTo(x * TILE_SIZE, 0).lineTo(x * TILE_SIZE, h);
  }
  for (let y = 0; y <= rows; y++) {
    g.moveTo(0, y * TILE_SIZE).lineTo(w, y * TILE_SIZE);
  }
  g.stroke({ width: 1, color: 0xffffff, alpha: 0.05 });
  g.rect(0, 0, w, h).stroke({ width: 2, color: 0xffffff, alpha: 0.12 });
  return g;
}

// 가구: 색 사각형 + 라벨 (스프라이트 도입 전 임시 표현)
function buildFurniture(PIXI: typeof import("pixi.js"), f: Furniture): Container {
  const c = new PIXI.Container();
  const w = f.w * TILE_SIZE;
  const h = f.h * TILE_SIZE;

  const g = new PIXI.Graphics();
  g.roundRect(2, 2, w - 4, h - 4, 6).fill({ color: f.color, alpha: f.passable ? 0.45 : 1 });
  if (!f.passable) {
    g.roundRect(2, 2, w - 4, h - 4, 6).stroke({ width: 2, color: 0x000000, alpha: 0.25 });
  }
  c.addChild(g);

  if (f.label) {
    const label = new PIXI.Text({
      text: f.label,
      style: { fontSize: 10, fill: 0xffffff },
    });
    label.anchor.set(0.5);
    label.position.set(w / 2, h / 2);
    label.alpha = 0.85;
    c.addChild(label);
  }

  c.position.set(f.x * TILE_SIZE, f.y * TILE_SIZE);
  return c;
}
