"use client";

import { Trash2, X } from "lucide-react";
import { useEditorStore } from "../stores/editorStore";

// 드래그 중 상단 중앙에 표시되는 드롭존 — 신규 배치는 취소(✕), 기존 가구는 삭제(휴지통)
export default function FurnitureDropZone() {
  const mode = useEditorStore((s) => s.drag?.mode ?? null);
  const overZone = useEditorStore((s) => s.drag?.overZone ?? false);

  if (!mode) return null;
  const isDelete = mode === "placed";

  return (
    <div
      data-furniture-dropout
      className={`absolute left-1/2 top-4 z-20 -translate-x-1/2 transition-transform duration-100 ${overZone ? "scale-125" : ""}`}
    >
      <div
        className={`flex h-14 w-14 items-center justify-center rounded-full border-2 shadow-lg backdrop-blur ${
          isDelete
            ? overZone
              ? "border-red-400 bg-red-500 text-white"
              : "border-red-400/60 bg-red-500/20 text-red-300"
            : overZone
              ? "border-white bg-white text-slate-900"
              : "border-white/60 bg-white/10 text-white/80"
        }`}
      >
        {isDelete ? <Trash2 className="h-6 w-6" /> : <X className="h-6 w-6" />}
      </div>
      <p className="mt-1 text-center text-[11px] font-medium text-white/70">{isDelete ? "삭제" : "취소"}</p>
    </div>
  );
}
