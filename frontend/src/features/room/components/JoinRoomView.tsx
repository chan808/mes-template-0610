"use client";

import { useRouter, useParams } from "next/navigation";
import { useRoomByInviteToken } from "../hooks/useRooms";

interface JoinRoomViewProps {
  token: string;
}

export default function JoinRoomView({ token }: JoinRoomViewProps) {
  const router = useRouter();
  const { locale } = useParams<{ locale: string }>();
  const { data: room, isLoading, isError } = useRoomByInviteToken(token);

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground">방 정보를 불러오는 중...</p>
      </div>
    );
  }

  if (isError || !room) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 text-center">
        <h2 className="text-xl font-semibold">유효하지 않은 초대 링크입니다</h2>
        <p className="text-muted-foreground">
          링크가 만료되었거나 존재하지 않는 방입니다.
        </p>
        <button
          onClick={() => router.push(`/${locale}/rooms`)}
          className="rounded-lg border border-border px-4 py-2 text-sm transition-colors hover:bg-muted"
        >
          방 목록으로 돌아가기
        </button>
      </div>
    );
  }

  const isClosed = room.status === "closed";

  function handleEnter() {
    router.push(`/${locale}/rooms/${room!.id}`);
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 p-6">
      <div className="w-full max-w-sm rounded-2xl border border-border bg-card p-8 shadow-lg">
        <div className="mb-6 text-center">
          <h1 className="text-xl font-bold text-card-foreground">방 입장</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            초대받은 방에 입장합니다
          </p>
        </div>

        <div className="mb-6 space-y-3 rounded-xl bg-muted/50 p-4 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">방 이름</span>
            <span className="font-medium text-card-foreground">{room.name}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">최대 인원</span>
            <span className="font-medium text-card-foreground">{room.maxCapacity}명</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">공개 여부</span>
            <span className="font-medium text-card-foreground">
              {room.isPrivate ? "비공개" : "공개"}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">상태</span>
            <span
              className={
                isClosed
                  ? "font-medium text-destructive"
                  : "font-medium text-green-600"
              }
            >
              {isClosed ? "닫힘" : "활성"}
            </span>
          </div>
        </div>

        {isClosed ? (
          <p className="text-center text-sm text-destructive">
            이 방은 현재 닫혀 있어 입장할 수 없습니다.
          </p>
        ) : (
          <button
            onClick={handleEnter}
            className="w-full rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
          >
            입장하기
          </button>
        )}
      </div>
    </div>
  );
}
