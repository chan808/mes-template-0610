import api from "@/shared/api/axios";
import { ApiResponse } from "@/shared/types/api";
import { LoginRequest, TokenResponse } from "../types/auth";

export const authApi = {
  login: (data: LoginRequest) =>
    api.post<ApiResponse<TokenResponse>>("/api/auth/login", data),

  reissue: () =>
    api.post<ApiResponse<TokenResponse>>("/api/auth/reissue", null, {
      headers: { "X-CSRF-GUARD": "1" },
    }),

  logout: () =>
    api.post<ApiResponse<void>>("/api/auth/logout", null, {
      headers: { "X-CSRF-GUARD": "1" },
    }),

  verifyEmail: (token: string) =>
    api.post<ApiResponse<void>>("/api/auth/verify-email", { token }),

  resendVerification: (email: string) =>
    api.post<ApiResponse<void>>("/api/auth/verify-email/resend", { email }),

  exchangeOAuthCode: (code: string) =>
    api.get<ApiResponse<{ accessToken: string }>>(`/api/auth/oauth2/token?code=${code}`),

  requestPasswordReset: (email: string) =>
    api.post<ApiResponse<void>>("/api/auth/password-reset/request", { email }),

  confirmPasswordReset: (token: string, newPassword: string) =>
    api.post<ApiResponse<void>>("/api/auth/password-reset/confirm", {
      token,
      newPassword,
    }),
};
