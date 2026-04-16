"use client";

import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import { useAuthStore } from "../stores/authStore";
import { authApi } from "../api/authApi";
import { LoginRequest } from "../types/auth";
import { resolvePostLoginPath } from "../utils/navigation";

export function useAuth() {
  const router = useRouter();
  const locale = useLocale();
  const { accessToken, setAccessToken, clearAuth } = useAuthStore();

  const login = async (data: LoginRequest, options?: { returnTo?: string | null }) => {
    const res = await authApi.login(data);
    setAccessToken(res.data.data!.accessToken);
    router.push(resolvePostLoginPath(locale, options?.returnTo));
  };

  const logout = async () => {
    await authApi.logout().catch(() => {}); // 백엔드 실패해도 클라이언트는 정리
    clearAuth();
    router.replace(`/${locale}/login`);
  };

  return {
    isAuthenticated: !!accessToken,
    login,
    logout,
  };
}
