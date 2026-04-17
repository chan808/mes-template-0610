"use client";

import { useQuery } from "@tanstack/react-query";
import { memberApi } from "@/features/member/api/memberApi";
import { useRooms } from "../hooks/useRooms";
import RoomCard from "./RoomCard";
import CreateRoomDialog from "./CreateRoomDialog";
import { Button } from "@/shared/components/ui/button";

export default function RoomListView() {
  const { data: rooms = [], isLoading: roomsLoading } = useRooms();
  const { data: me } = useQuery({
    queryKey: ["member", "me"],
    queryFn: () => memberApi.getMyInfo().then((res) => res.data.data!),
  });

  if (roomsLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground">불러오는 중...</p>
      </div>
    );
  }

  return (
    <main className="min-h-screen p-6 sm:p-10">
      <div className="mx-auto max-w-4xl">
        <div className="mb-8 flex items-center justify-between">
          <h1 className="text-2xl font-bold">내 방 목록</h1>
          <CreateRoomDialog>
            <Button>+ 새 방 만들기</Button>
          </CreateRoomDialog>
        </div>

        {rooms.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-4 rounded-xl border border-dashed border-border py-20 text-center">
            <p className="text-muted-foreground">아직 참여한 방이 없습니다.</p>
            <CreateRoomDialog>
              <Button variant="outline">첫 번째 방 만들기</Button>
            </CreateRoomDialog>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {rooms.map((room) => (
              <RoomCard
                key={room.id}
                room={room}
                currentUserId={me?.id ?? -1}
              />
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
