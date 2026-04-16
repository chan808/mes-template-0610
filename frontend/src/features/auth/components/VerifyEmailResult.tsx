"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations, useLocale } from "next-intl";
import Link from "next/link";
import { authApi } from "@/features/auth/api/authApi";
import { Button } from "@/shared/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/shared/components/ui/card";

type Status = "idle" | "loading" | "success" | "error";

export default function VerifyEmailResult() {
  const t = useTranslations("auth.verifyEmail");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<Status>(token ? "idle" : "error");

  const handleVerify = async () => {
    if (!token || status === "loading") return;

    setStatus("loading");
    try {
      await authApi.verifyEmail(token);
      setStatus("success");
    } catch {
      setStatus("error");
    }
  };

  return (
    <Card className="w-full max-w-md text-center">
      <CardHeader>
        <CardTitle className="text-2xl">{t(`${status}.title`)}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground">{t(`${status}.description`)}</p>
        {status === "idle" && token && (
          <Button className="w-full" onClick={handleVerify}>
            {t("idle.action")}
          </Button>
        )}
        {status === "success" && (
          <Button asChild className="w-full">
            <Link href={`/${locale}/login`}>{t("success.action")}</Link>
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
