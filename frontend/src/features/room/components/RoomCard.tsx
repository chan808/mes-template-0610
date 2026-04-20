"use client";

import { useRouter, useParams } from "next/navigation";
import { RoomSummary } from "../types/room";

interface RoomCardProps {
  room: RoomSummary;
  currentUserId: number;
}

export default function RoomCard({ room, currentUserId }: RoomCardProps) {
  const router = useRouter();
  const { locale } = useParams<{ locale: string }>();

  function handleEnter() {
    router.push(`/${locale}/rooms/${room.id}`);
  }

  const isOwner = room.ownerId === currentUserId;
  const isClosed = room.status === "closed";

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-card p-5 shadow-sm transition-shadow hover:shadow-md">
      <div className="flex items-start justify-between gap-2">
        <h3 className="truncate text-base font-semibold text-card-foreground">
          {room.name}
        </h3>
        <div className="flex shrink-0 items-center gap-1.5">
          {room.isPrivate && (
            <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
              비공개
            </span>
          )}
          {isOwner && (
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary">
              내 방
            </span>
          )}
          {isClosed && (
            <span className="rounded-full bg-destructive/10 px-2 py-0.5 text-xs text-destructive">
              닫힘
            </span>
          )}
        </div>
      </div>
      <p className="text-sm text-muted-foreground">
        최대 {room.maxCapacity}명
      </p>
      <button
        onClick={handleEnter}
        disabled={isClosed}
        className="mt-auto w-full rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
      >
        입장
      </button>
    </div>
  );
}
