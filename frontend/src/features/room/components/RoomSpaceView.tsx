"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter, useParams } from "next/navigation";
import { userApi } from "@/features/member/api/userApi";
import { roomApi } from "../api/roomApi";
import SpaceCanvas from "./SpaceCanvas";
import ChatPanel from "./ChatPanel";
import MemberList from "./MemberList";
import HumanInLoopDialog from "./HumanInLoopDialog";
import { useWebSocket } from "../hooks/useWebSocket";
import { roomKeys } from "../hooks/useRooms";

interface RoomSpaceViewProps {
  roomId: number;
}

function RoomSpaceInner({ roomId, myUserId, myNickname }: { roomId: number; myUserId: number; myNickname: string }) {
  const { send } = useWebSocket(roomId);

  return (
    <>
      <div className="flex h-screen flex-col">
        {/* 상단 헤더 */}
        <header className="flex items-center justify-between border-b border-border bg-card px-5 py-3">
          <h1 className="text-base font-semibold">공간</h1>
        </header>

        {/* 본문 레이아웃: 캔버스 + 사이드바 */}
        <div className="flex flex-1 overflow-hidden">
          {/* 캔버스 영역 (스크롤 가능) */}
          <div className="flex-1 overflow-auto p-4">
            <SpaceCanvas myUserId={myUserId} myNickname={myNickname} onSend={send} />
          </div>

          {/* 사이드바: 접속자 목록 + 채팅 */}
          <aside className="flex w-80 shrink-0 flex-col gap-3 border-l border-border p-3">
            <MemberList myUserId={myUserId} myNickname={myNickname} onSend={send} />
            <div className="flex-1 min-h-0">
              <ChatPanel roomId={roomId} myUserId={myUserId} onSend={send} />
            </div>
          </aside>
        </div>
      </div>

      {/* HitL 다이얼로그: 에이전트가 사용자 입력 요청 시 오버레이 표시 */}
      <HumanInLoopDialog onSend={send} />
    </>
  );
}

export default function RoomSpaceView({ roomId }: RoomSpaceViewProps) {
  const router = useRouter();
  const { locale } = useParams<{ locale: string }>();

  const { data: me, isLoading: meLoading } = useQuery({
    queryKey: ["user", "me"],
    queryFn: () => userApi.getMyInfo().then((res) => res.data.data!),
  });

  const { data: room, isLoading: roomLoading, isError } = useQuery({
    queryKey: roomKeys.detail(roomId),
    queryFn: () => roomApi.getRoom(roomId).then((res) => res.data.data!),
    retry: false,
  });

  if (meLoading || roomLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground">공간에 입장하는 중...</p>
      </div>
    );
  }

  if (isError || !room) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 text-center">
        <h2 className="text-xl font-semibold">방을 찾을 수 없습니다</h2>
        <button
          onClick={() => router.push(`/${locale}/rooms`)}
          className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-muted"
        >
          방 목록으로
        </button>
      </div>
    );
  }

  if (room.status === "closed") {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 text-center">
        <h2 className="text-xl font-semibold">닫힌 방입니다</h2>
        <button
          onClick={() => router.push(`/${locale}/rooms`)}
          className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-muted"
        >
          방 목록으로
        </button>
      </div>
    );
  }

  if (!me) return null;

  return (
    <RoomSpaceInner
      roomId={roomId}
      myUserId={me.id}
      myNickname={me.nickname ?? me.email}
    />
  );
}
