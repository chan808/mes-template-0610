import axios, {
  AxiosError,
  InternalAxiosRequestConfig,
} from "axios";
import { useAuthStore } from "@/features/auth/stores/authStore";
import {
  buildAuthPageHref,
  buildCurrentPath,
  resolveLocaleFromPathname,
} from "@/features/auth/utils/navigation";

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token!);
  });
  failedQueue = [];
};

interface RetryableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const isAuthEndpoint = (url?: string): boolean =>
  url?.includes("/api/auth/") ?? false;

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetryableConfig | undefined;

    if (
      error.response?.status !== 401 ||
      !original ||
      original._retry ||
      isAuthEndpoint(original.url)
    ) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then((token) => {
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      });
    }

    original._retry = true;
    isRefreshing = true;

    try {
      const { data } = await axios.post(
        `${process.env.NEXT_PUBLIC_API_URL}/api/auth/reissue`,
        null,
        {
          withCredentials: true,
          headers: { "X-CSRF-GUARD": "1" },
        },
      );
      const newToken: string = data.data.accessToken;
      useAuthStore.getState().setAccessToken(newToken);
      processQueue(null, newToken);
      original.headers.Authorization = `Bearer ${newToken}`;
      return api(original);
    } catch (refreshError) {
      processQueue(refreshError, null);
      useAuthStore.getState().clearAuth();

      const locale = resolveLocaleFromPathname(window.location.pathname);
      const returnTo = buildCurrentPath(
        window.location.pathname,
        new URLSearchParams(window.location.search),
      );

      window.location.replace(
        buildAuthPageHref({
          locale,
          page: "login",
          returnTo,
        }),
      );

      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

export default api;
