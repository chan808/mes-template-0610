import api from "@/shared/api/axios";
import { ApiResponse } from "@/shared/types/api";
import { MemberResponse, SignupRequest } from "@/features/auth/types/auth";

export const memberApi = {
  signup: (data: SignupRequest) => api.post<ApiResponse<void>>("/api/members", data),

  getMyInfo: () => api.get<ApiResponse<MemberResponse>>("/api/members/me"),

  updateProfile: (data: { nickname: string | null }) =>
    api.patch<ApiResponse<MemberResponse>>("/api/members/me/profile", data),

  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    api.patch<ApiResponse<void>>("/api/members/me/password", data),

  withdraw: () => api.delete<ApiResponse<void>>("/api/members/me"),
};
