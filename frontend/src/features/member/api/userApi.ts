import api from "@/shared/api/axios";
import { ApiResponse } from "@/shared/types/api";
import { UserResponse, SignupRequest } from "@/features/auth/types/auth";

export const userApi = {
  signup: (data: SignupRequest) => api.post<ApiResponse<void>>("/api/v1/users", data),

  getMyInfo: () => api.get<ApiResponse<UserResponse>>("/api/v1/users/me"),

  updateProfile: (data: { nickname: string | null }) =>
    api.patch<ApiResponse<UserResponse>>("/api/v1/users/me", data),

  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    api.patch<ApiResponse<void>>("/api/v1/users/me/password", data),

  withdraw: () => api.delete<ApiResponse<void>>("/api/v1/users/me"),
};
