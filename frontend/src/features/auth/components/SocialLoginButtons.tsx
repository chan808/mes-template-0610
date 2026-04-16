"use client";

import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/shared/components/ui/button";

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";
const OAUTH_ENABLED = process.env.NEXT_PUBLIC_OAUTH_ENABLED !== "false";
const ENABLED_PROVIDERS = (process.env.NEXT_PUBLIC_OAUTH_PROVIDERS ?? "")
  .split(",")
  .map((provider) => provider.trim().toLowerCase())
  .filter(Boolean);

const providers = [
  { id: "google", label: "Google" },
  { id: "naver",  label: "Naver" },
  { id: "kakao",  label: "Kakao" },
] as const;

interface SocialLoginButtonsProps {
  returnTo?: string | null;
}

export default function SocialLoginButtons({ returnTo }: SocialLoginButtonsProps) {
  const t = useTranslations("auth.social");
  const locale = useLocale();
  const visibleProviders = providers.filter(({ id }) =>
    ENABLED_PROVIDERS.includes(id),
  );

  const handleLogin = (provider: string) => {
    const url = new URL(`${BACKEND_URL}/oauth2/authorization/${provider}`);
    url.searchParams.set("locale", locale);
    if (returnTo) {
      url.searchParams.set("returnTo", returnTo);
    }
    window.location.assign(url.toString());
  };

  if (!OAUTH_ENABLED || visibleProviders.length === 0) {
    return null;
  }

  return (
    <div className="space-y-2">
      <div className="relative my-4">
        <div className="absolute inset-0 flex items-center">
          <span className="w-full border-t" />
        </div>
        <div className="relative flex justify-center text-xs uppercase">
          <span className="bg-card px-2 text-muted-foreground">{t("divider")}</span>
        </div>
      </div>
      {visibleProviders.map(({ id, label }) => (
        <Button
          key={id}
          variant="outline"
          className="w-full"
          onClick={() => handleLogin(id)}
        >
          {t("loginWith", { provider: label })}
        </Button>
      ))}
    </div>
  );
}
