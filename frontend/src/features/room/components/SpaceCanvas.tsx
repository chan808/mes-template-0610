"use client";

import { useEffect, useRef } from "react";
import type { Application, Container, Graphics, Ticker } from "pixi.js";
import { usePresenceStore } from "../stores/presenceStore";
import { useComposerStore } from "../stores/composerStore";
import { PlacedFurniture, canEditFurniture, useFurnitureStore } from "../stores/furnitureStore";
import { useEditorStore } from "../stores/editorStore";
import { useTileMovement } from "../hooks/useTileMovement";
import { useRoomMap } from "../hooks/useRoomMap";
import { Direction, MapSpec, SPAWN_TILE, TILE_SIZE, TilePos, tileToPixel } from "../lib/tile";
import { TileWalker } from "../lib/tileWalker";
import { cameraOffset } from "../lib/camera";
import { canPlace, effectiveSize } from "../lib/furniture";
import { anchorTopLeft } from "../lib/furnitureCatalog";
import { ClientMessage } from "../types/ws";
import FurniturePanel from "./FurniturePanel";
import FurnitureDropZone from "./FurnitureDropZone";

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
  roomOwnerId: number;
  onSend: (msg: ClientMessage) => void;
}

export default function SpaceCanvas({ myUserId, myNickname, roomOwnerId, onSend }: SpaceCanvasProps) {
  // 가구 스토어에서 파생된 충돌맵 — 가구 변경이 이동에 즉시 반영
  const { spec, map } = useRoomMap();
  useTileMovement(onSend, map);

  const panelOpen = useEditorStore((s) => s.panelOpen);
  const togglePanel = useEditorStore((s) => s.togglePanel);

  const hostRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    let disposed = false;
    let app: Application | undefined;
    const cleanups: (() => void)[] = [];

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

      const worldW = spec.cols * TILE_SIZE;
      const worldH = spec.rows * TILE_SIZE;

      // 레이어: 바닥 → 통과 가구 → (차단 가구 + 아바타, y 정렬) → 편집 격자 → 고스트
      const world = new PIXI.Container();
      const passableLayer = new PIXI.Container();
      const entityLayer = new PIXI.Container();
      entityLayer.sortableChildren = true;
      const gridOverlay = buildEditGrid(PIXI, spec);
      gridOverlay.visible = false;
      const ghost = new PIXI.Graphics();

      world.addChild(buildFloor(PIXI, spec.cols, spec.rows));
      world.addChild(passableLayer);
      world.addChild(entityLayer);
      world.addChild(gridOverlay);
      world.addChild(ghost);
      instance.stage.addChild(world);

      // ───────────────────────── 아바타 ─────────────────────────
      const nodes = new Map<string, AvatarNode>();
      const meKey = `user-${myUserId}`;

      // 더블탭으로 채팅 타겟 설정: 유저 → 귓속말, 에이전트 → @멘션 (ADR-0002)
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

      const createAvatarNode = (t: AvatarTarget): AvatarNode => {
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

      // presence 스토어 → 아바타 노드 동기화
      const syncAvatars = (state: ReturnType<typeof usePresenceStore.getState>) => {
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
            nodes.set(key, createAvatarNode(t));
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

      // ───────────────────────── 가구 ─────────────────────────
      const furnitureNodes = new Map<string, { root: Container; item: PlacedFurniture }>();

      // 편집 상태 반영: 편집 모드에서 권한 있는 가구만 잡을 수 있고, 드래그 중인 원본은 흐리게
      const applyEditState = () => {
        const { panelOpen: editing, drag } = useEditorStore.getState();
        for (const [id, node] of furnitureNodes) {
          const editable = editing && canEditFurniture(node.item, myUserId, roomOwnerId);
          node.root.eventMode = editable ? "static" : "none";
          node.root.cursor = "grab";
          node.root.alpha = drag?.mode === "placed" && drag.id === id ? 0.35 : 1;
        }
      };

      const createFurnitureNode = (item: PlacedFurniture) => {
        const root = buildFurnitureNode(PIXI, item);
        // 발밑(y)이 더 아래인 쪽이 앞에 보이도록 타일 단위 zIndex
        if (item.passable) {
          passableLayer.addChild(root);
        } else {
          root.zIndex = item.y + effectiveSize(item.w, item.h, item.rotation).h;
          entityLayer.addChild(root);
        }
        // 편집 모드에서 잡기(픽업) — 권한은 applyEditState가 eventMode로 제어
        root.on("pointerdown", () => {
          const editor = useEditorStore.getState();
          if (!editor.panelOpen || editor.drag) return;
          const current = useFurnitureStore.getState().items.get(item.id);
          if (!current || !canEditFurniture(current, myUserId, roomOwnerId)) return;
          editor.startPlacedDrag(item.id, {
            kind: current.kind,
            label: current.label ?? "",
            w: current.w,
            h: current.h,
            passable: current.passable,
            color: current.color,
          }, current.rotation);
        });
        furnitureNodes.set(item.id, { root, item });
      };

      // 가구 스토어 → 가구 노드 동기화 (수가 적어 변경 시 노드 재생성)
      const syncFurniture = (items: Map<string, PlacedFurniture>) => {
        for (const [id, item] of items) {
          const node = furnitureNodes.get(id);
          if (!node) {
            createFurnitureNode(item);
          } else if (node.item.x !== item.x || node.item.y !== item.y || node.item.rotation !== item.rotation) {
            node.root.destroy({ children: true });
            furnitureNodes.delete(id);
            createFurnitureNode(item);
          }
        }
        for (const [id, node] of furnitureNodes) {
          if (!items.has(id)) {
            node.root.destroy({ children: true });
            furnitureNodes.delete(id);
          }
        }
        applyEditState();
      };

      // ─────────────────── 가구 드래그(배치·이동) ───────────────────
      let lastClient = { x: 0, y: 0 };

      // 포인터 위치 → 고스트 타일과 배치 가능 여부 계산
      const computeDragTile = () => {
        const editor = useEditorStore.getState();
        const drag = editor.drag;
        if (!drag) return;

        const overEl = document.elementFromPoint(lastClient.x, lastClient.y);
        const overZone = !!overEl?.closest("[data-furniture-dropout]");
        const overCancel = !!overEl?.closest("[data-furniture-cancel]");
        editor.setOverZone(overZone);

        const rect = instance.canvas.getBoundingClientRect();
        const inCanvas =
          lastClient.x >= rect.left && lastClient.x < rect.right &&
          lastClient.y >= rect.top && lastClient.y < rect.bottom;

        if (!inCanvas || overZone || overCancel) {
          editor.setDragTile(null, false);
          return;
        }

        const wx = lastClient.x - rect.left - world.position.x;
        const wy = lastClient.y - rect.top - world.position.y;
        const pointerTile = { x: Math.floor(wx / TILE_SIZE), y: Math.floor(wy / TILE_SIZE) };
        const topLeft = anchorTopLeft(pointerTile, drag.entry.w, drag.entry.h, drag.rotation);
        const items = [...useFurnitureStore.getState().items.values()];
        const valid = canPlace(items, spec, {
          ...topLeft,
          w: drag.entry.w,
          h: drag.entry.h,
          rotation: drag.rotation,
          passable: drag.entry.passable,
          excludeId: drag.id ?? undefined,
        });
        editor.setDragTile(topLeft, valid);
      };

      const onPointerMove = (e: PointerEvent) => {
        lastClient = { x: e.clientX, y: e.clientY };
        if (useEditorStore.getState().drag) computeDragTile();
      };

      // 드롭 확정: 드롭존 → 취소/삭제, 유효 타일 → 배치/이동, 그 외 → 취소
      const onPointerUp = () => {
        const editor = useEditorStore.getState();
        const drag = editor.drag;
        if (!drag) return;
        const store = useFurnitureStore.getState();

        if (drag.overZone) {
          if (drag.mode === "placed" && drag.id) store.remove(drag.id, myUserId, roomOwnerId);
        } else if (drag.tile && drag.valid) {
          if (drag.mode === "palette") {
            store.place({
              id: crypto.randomUUID(),
              kind: drag.entry.kind,
              x: drag.tile.x,
              y: drag.tile.y,
              w: drag.entry.w,
              h: drag.entry.h,
              rotation: drag.rotation,
              passable: drag.entry.passable,
              color: drag.entry.color,
              label: drag.entry.label,
              placedBy: myUserId,
            }, spec);
          } else if (drag.id) {
            store.move(drag.id, { x: drag.tile.x, y: drag.tile.y, rotation: drag.rotation }, myUserId, roomOwnerId, spec);
          }
        }
        editor.endDrag();
      };

      const onKeyDown = (e: KeyboardEvent) => {
        const editor = useEditorStore.getState();
        if (!editor.drag) return;
        if (e.code === "KeyR") {
          e.preventDefault();
          editor.rotateDrag();
          computeDragTile();
        } else if (e.code === "Escape") {
          editor.endDrag();
        }
      };

      // 드래그 중 우클릭은 취소
      const onContextMenu = (e: MouseEvent) => {
        if (useEditorStore.getState().drag) {
          e.preventDefault();
          useEditorStore.getState().endDrag();
        }
      };

      // 포인터 강제 중단(브라우저 제스처 등) 시 드래그 취소
      const onPointerCancel = () => {
        if (useEditorStore.getState().drag) useEditorStore.getState().endDrag();
      };

      window.addEventListener("pointermove", onPointerMove);
      window.addEventListener("pointerup", onPointerUp);
      window.addEventListener("pointercancel", onPointerCancel);
      window.addEventListener("keydown", onKeyDown);
      window.addEventListener("contextmenu", onContextMenu);
      cleanups.push(() => {
        window.removeEventListener("pointermove", onPointerMove);
        window.removeEventListener("pointerup", onPointerUp);
        window.removeEventListener("pointercancel", onPointerCancel);
        window.removeEventListener("keydown", onKeyDown);
        window.removeEventListener("contextmenu", onContextMenu);
      });

      // 고스트(footprint 미리보기): 초록=배치 가능, 빨강=불가
      const redrawGhost = () => {
        const drag = useEditorStore.getState().drag;
        ghost.clear();
        if (!drag?.tile) return;
        const size = effectiveSize(drag.entry.w, drag.entry.h, drag.rotation);
        const x = drag.tile.x * TILE_SIZE;
        const y = drag.tile.y * TILE_SIZE;
        const w = size.w * TILE_SIZE;
        const h = size.h * TILE_SIZE;
        ghost.roundRect(x + 2, y + 2, w - 4, h - 4, 6).fill({ color: drag.entry.color, alpha: 0.45 });
        ghost.roundRect(x + 2, y + 2, w - 4, h - 4, 6).stroke({ width: 3, color: drag.valid ? 0x22c55e : 0xef4444 });
      };

      // ─────────────────── 스토어 구독 + 프레임 루프 ───────────────────
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

      syncAvatars(usePresenceStore.getState());
      syncFurniture(useFurnitureStore.getState().items);
      cleanups.push(usePresenceStore.subscribe(syncAvatars));
      cleanups.push(useFurnitureStore.subscribe((s) => syncFurniture(s.items)));
      cleanups.push(
        useEditorStore.subscribe((s) => {
          gridOverlay.visible = s.panelOpen;
          document.body.style.cursor = s.drag ? "grabbing" : "";
          redrawGhost();
          applyEditState();
        }),
      );
      gridOverlay.visible = useEditorStore.getState().panelOpen;
      instance.ticker.add(tick);
    })();

    return () => {
      disposed = true;
      for (const fn of cleanups) fn();
      document.body.style.cursor = "";
      if (app) {
        app.destroy(true, { children: true });
        app = undefined;
      }
    };
  }, [spec, myUserId, myNickname, roomOwnerId]);

  return (
    <div className="relative h-full w-full overflow-hidden rounded-xl border border-border bg-slate-900">
      <div ref={hostRef} className="absolute inset-0" />

      {/* 가구 편집 토글 */}
      <button
        type="button"
        onClick={togglePanel}
        className={`absolute right-3 top-3 z-10 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors ${
          panelOpen
            ? "border-indigo-400 bg-indigo-500 text-white"
            : "border-white/15 bg-black/50 text-white/80 hover:bg-black/70"
        }`}
      >
        {panelOpen ? "편집 완료" : "가구 편집"}
      </button>

      {panelOpen && <FurniturePanel />}
      <FurnitureDropZone />

      {/* 조작 안내 */}
      <div className="pointer-events-none absolute bottom-3 right-3 z-10 rounded-lg bg-black/50 px-2 py-1 text-xs text-white/60">
        {panelOpen
          ? "드래그 배치 · R 회전 · ESC/우클릭 취소"
          : "WASD / 방향키 이동 · 아바타 더블클릭: 귓속말/에이전트 지정"}
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

// 편집 모드 격자: 진한 격자 + 스폰 타일 표시
function buildEditGrid(PIXI: typeof import("pixi.js"), spec: MapSpec): Graphics {
  const g = new PIXI.Graphics();
  const w = spec.cols * TILE_SIZE;
  const h = spec.rows * TILE_SIZE;

  for (let x = 0; x <= spec.cols; x++) {
    g.moveTo(x * TILE_SIZE, 0).lineTo(x * TILE_SIZE, h);
  }
  for (let y = 0; y <= spec.rows; y++) {
    g.moveTo(0, y * TILE_SIZE).lineTo(w, y * TILE_SIZE);
  }
  g.stroke({ width: 1, color: 0x818cf8, alpha: 0.18 });

  // 스폰 타일: 차단 가구 배치 불가 표시
  const { px, py } = tileToPixel(SPAWN_TILE);
  g.circle(px, py, TILE_SIZE * 0.32).stroke({ width: 2, color: 0x818cf8, alpha: 0.6 });
  return g;
}

// 가구 노드: 색 사각형(회전 반영) + 정면 표시 띠 + 라벨 (스프라이트 도입 전 임시 표현)
function buildFurnitureNode(PIXI: typeof import("pixi.js"), f: PlacedFurniture): Container {
  const c = new PIXI.Container();
  const size = effectiveSize(f.w, f.h, f.rotation);
  const w = size.w * TILE_SIZE;
  const h = size.h * TILE_SIZE;

  const g = new PIXI.Graphics();
  g.roundRect(2, 2, w - 4, h - 4, 6).fill({ color: f.color, alpha: f.passable ? 0.45 : 1 });
  if (!f.passable) {
    g.roundRect(2, 2, w - 4, h - 4, 6).stroke({ width: 2, color: 0x000000, alpha: 0.25 });
    // 정면(회전 기준) 가장자리 띠 — 0=아래, 1=왼쪽, 2=위, 3=오른쪽
    const t = 6;
    const strip: Record<number, [number, number, number, number]> = {
      0: [4, h - 4 - t, w - 8, t],
      1: [4, 4, t, h - 8],
      2: [4, 4, w - 8, t],
      3: [w - 4 - t, 4, t, h - 8],
    };
    const [sx, sy, sw, sh] = strip[f.rotation];
    g.roundRect(sx, sy, sw, sh, 3).fill({ color: 0x000000, alpha: 0.2 });
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
