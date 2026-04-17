import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
    queryFn: () => roomApi.getRooms().then((res) => res.data.data ?? []),
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
    },
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
    },
  });
}

export function useDeleteRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => roomApi.deleteRoom(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomKeys.list() });
    },
  });
}

export function useRegenerateInviteToken(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      roomApi.regenerateInviteToken(id).then((res) => res.data.data!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomKeys.detail(id) });
    },
  });
}
