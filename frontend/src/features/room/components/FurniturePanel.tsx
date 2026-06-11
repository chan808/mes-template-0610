"use client";

import { FURNITURE_CATALOG } from "../lib/furnitureCatalog";
import { useEditorStore } from "../stores/editorStore";

// 가구 선택 패널 — 항목을 누른 채 맵으로 끌어 배치한다. 패널 위에 놓으면 취소(data-furniture-cancel)
export default function FurniturePanel() {
  const startPaletteDrag = useEditorStore((s) => s.startPaletteDrag);

  return (
    <div
      data-furniture-cancel
      className="absolute bottom-3 left-3 top-3 z-10 w-56 overflow-y-auto rounded-xl border border-white/10 bg-slate-950/90 p-3 backdrop-blur"
    >
      <h3 className="mb-1 text-sm font-semibold text-white">가구</h3>
      <p className="mb-3 text-xs leading-relaxed text-white/50">
        맵으로 드래그해 배치
        <br />
        드래그 중 R 회전 · ESC 취소
      </p>
      <ul className="space-y-1.5">
        {FURNITURE_CATALOG.map((entry) => (
          <li key={entry.kind}>
            <button
              type="button"
              onPointerDown={(e) => {
                e.preventDefault();
                startPaletteDrag(entry);
              }}
              className="flex w-full cursor-grab select-none items-center gap-3 rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-left hover:bg-white/10 active:cursor-grabbing"
            >
              <span
                className="shrink-0 rounded-sm"
                style={{
                  width: entry.w * 7,
                  height: entry.h * 7,
                  backgroundColor: `#${entry.color.toString(16).padStart(6, "0")}`,
                  opacity: entry.passable ? 0.55 : 1,
                }}
              />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm text-white">{entry.label}</span>
                <span className="text-[11px] text-white/45">
                  {entry.w}×{entry.h}
                  {entry.passable ? " · 통과 가능" : ""}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
