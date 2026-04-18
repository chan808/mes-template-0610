import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { roomApi } from "../api/roomApi";
import { CreateRoomRequest, UpdateRoomRequest } from "../types/room";

export const roomKeys = {
  all: ["rooms"] as const,
  list: () => [...roomKeys.all, "list"] as const,
  detail: (id: number) => [...roomKeys.all, "detail", id] as const,
  join: (token: string) => [...roomKeys.all, "join", token] as const,
};

export function useRooms() {
  return useQuery({
    queryKey: roomKeys.list(),
    queryFn: () => roomApi.getRooms().then((res) => res.data.data?.content ?? []),
  });
}

export function useRoom(id: number) {
  return useQuery({
    queryKey: roomKeys.detail(id),
    queryFn: () => roomApi.getRoom(id).then((res) => res.data.data!),
    enabled: !!id,
  });
}

export function useRoomByInviteToken(token: string) {
  return useQuery({
    queryKey: roomKeys.join(token),
    queryFn: () =>
      roomApi.getRoomByInviteToken(token).then((res) => res.data.data!),
    enabled: !!token,
    retry: false,
  });
}

export function useCreateRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateRoomRequest) =>
      roomApi.createRoom(data).then((res) => res.data.data!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomKeys.list() });
      toast.success("방이 생성되었습니다.");
    },
    onError: () => toast.error("방 생성에 실패했습니다."),
  });
}

export function useUpdateRoom(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateRoomRequest) =>
      roomApi.updateRoom(id, data).then((res) => res.data.data!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: roomKeys.list() });
      toast.success("방 설정이 저장되었습니다.");
    },
    onError: () => toast.error("방 설정 변경에 실패했습니다."),
  });
}

export function useDeleteRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => roomApi.deleteRoom(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomKeys.list() });
      toast.success("방이 삭제되었습니다.");
    },
    onError: () => toast.error("방 삭제에 실패했습니다."),
  });
}

export function useRegenerateInviteToken(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      roomApi.regenerateInviteToken(id).then((res) => res.data.data!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomKeys.detail(id) });
      toast.success("초대 링크가 재생성되었습니다. 기존 링크는 즉시 무효화됩니다.");
    },
    onError: () => toast.error("초대 링크 재생성에 실패했습니다."),
  });
}
