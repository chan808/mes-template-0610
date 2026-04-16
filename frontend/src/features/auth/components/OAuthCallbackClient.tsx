"use client";

import { useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useLocale } from "next-intl";
import { authApi } from "@/features/auth/api/authApi";
import { useAuthStore } from "@/features/auth/stores/authStore";
import {
  buildAuthPageHref,
  normalizeReturnTo,
  resolvePostLoginPath,
} from "@/features/auth/utils/navigation";

export default function OAuthCallbackClient() {
  const router = useRouter();
  const locale = useLocale();
  const searchParams = useSearchParams();
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  const hasExchanged = useRef(false);

  useEffect(() => {
    if (hasExchanged.current) return;
    hasExchanged.current = true;

    const code = searchParams.get("code");
    const error = searchParams.get("error");
    const returnTo = normalizeReturnTo(searchParams.get("returnTo"));

    if (error) {
      router.replace(buildAuthPageHref({
        locale,
        page: "login",
        returnTo,
        error,
      }));
      return;
    }

    if (!code) {
      router.replace(buildAuthPageHref({
        locale,
        page: "login",
        returnTo,
      }));
      return;
    }

    authApi
      .exchangeOAuthCode(code)
      .then((res) => {
        const at = res.data.data?.accessToken;
        if (at) setAccessToken(at);
        router.replace(resolvePostLoginPath(locale, returnTo));
      })
      .catch(() => {
        router.replace(buildAuthPageHref({
          locale,
          page: "login",
          returnTo,
          error: "OAuth login failed.",
        }));
      });
  }, [locale, router, searchParams, setAccessToken]);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-muted-foreground">Processing login...</p>
    </main>
  );
}
