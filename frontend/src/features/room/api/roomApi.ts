import api from "@/shared/api/axios";
import { ApiResponse } from "@/shared/types/api";
import {
  CreateRoomRequest,
  Room,
  RoomJoinInfo,
  RoomSummary,
  UpdateRoomRequest,
} from "../types/room";

export const roomApi = {
  createRoom: (data: CreateRoomRequest) =>
    api.post<ApiResponse<Room>>("/api/v1/rooms", data),

  getRooms: () =>
    api.get<ApiResponse<RoomSummary[]>>("/api/v1/rooms"),

  getRoom: (id: number) =>
    api.get<ApiResponse<Room>>(`/api/v1/rooms/${id}`),

  updateRoom: (id: number, data: UpdateRoomRequest) =>
    api.patch<ApiResponse<Room>>(`/api/v1/rooms/${id}`, data),

  deleteRoom: (id: number) =>
    api.delete<ApiResponse<void>>(`/api/v1/rooms/${id}`),

  regenerateInviteToken: (id: number) =>
    api.post<ApiResponse<Room>>(`/api/v1/rooms/${id}/invite`),

  // 초대 토큰으로 방 정보 조회
  getRoomByInviteToken: (token: string) =>
    api.get<ApiResponse<RoomJoinInfo>>(`/api/v1/rooms/join/${token}`),
};
