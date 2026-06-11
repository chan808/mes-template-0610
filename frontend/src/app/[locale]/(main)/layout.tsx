"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import { useAuthStore } from "@/features/auth/stores/authStore";
import { authApi } from "@/features/auth/api/authApi";
import { buildAuthPageHref } from "@/features/auth/utils/navigation";

// This starter protects the authenticated shell on the client.
// Access tokens live in memory, so the layout restores auth with reissue()
// after hydration instead of performing server-side authorization here.
export default function MainLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const locale = useLocale();
  const { setAccessToken, clearAuth } = useAuthStore();
  // 토큰 보유 시 lazy 초기값으로 처리 — 효과 내 동기 setState 제거 (react-hooks/set-state-in-effect)
  const [ready, setReady] = useState(() => !!useAuthStore.getState().accessToken);

  useEffect(() => {
    if (useAuthStore.getState().accessToken) return;

    authApi
      .reissue()
      .then(({ data }) => {
        setAccessToken(data.data!.accessToken);
        setReady(true);
      })
      .catch(() => {
        clearAuth();
        const returnTo = `${window.location.pathname}${window.location.search}`;
        router.replace(
          buildAuthPageHref({
            locale,
            page: "login",
            returnTo,
          }),
        );
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!ready) return null;

  return <>{children}</>;
}
