import api from "@/shared/api/axios";
import { ApiResponse } from "@/shared/types/api";

export interface HistoryMessage {
  id: number;
  userId: number | null;
  nickname: string | null;
  content: string;
  type: "chat" | "system" | "agent";
  createdAt: string;
  // type=agent일 때 표시용 닉네임
  agentNickname?: string | null;
}

export interface MessagesResponse {
  messages: HistoryMessage[];
  hasMore: boolean;
}

export const messageApi = {
  getMessages: (roomId: number, params?: { before?: number; limit?: number }) =>
    api.get<ApiResponse<MessagesResponse>>(`/api/v1/rooms/${roomId}/messages`, {
      params: { limit: 50, ...params },
    }),
};
