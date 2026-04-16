import { create } from "zustand";

interface AuthState {
  accessToken: string | null;
  setAccessToken: (token: string) => void;
  clearAuth: () => void;
}

// AT는 메모리에만 보관 — XSS로부터 보호, 새로고침 시 reissue로 복구
// 서버 데이터(member 정보 등)는 React Query로 관리 — 동기화 복잡도 제거
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  setAccessToken: (token) => set({ accessToken: token }),
  clearAuth: () => set({ accessToken: null }),
}));
