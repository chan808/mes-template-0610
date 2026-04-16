"use client";

import { useState } from "react";
import { useTranslations, useLocale } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { AxiosError } from "axios";
import { useAuth } from "../hooks/useAuth";
import { authApi } from "../api/authApi";
import { useCooldown } from "../hooks/useCooldown";
import { getRetryAfterSeconds } from "../utils/retryAfter";
import SocialLoginButtons from "./SocialLoginButtons";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/shared/components/ui/form";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/shared/components/ui/card";
import {
  buildAuthPageHref,
  normalizeReturnTo,
} from "../utils/navigation";

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

type FormData = z.infer<typeof schema>;

export default function LoginForm() {
  const t = useTranslations("auth.login");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const resetSuccess = searchParams.get("reset") === "success";
  const oauthError = searchParams.get("error");
  const returnTo = normalizeReturnTo(searchParams.get("returnTo"));
  const initialEmail = searchParams.get("email")?.trim() ?? "";
  const { login } = useAuth();
  const { secondsLeft, startCooldown, isCoolingDown } = useCooldown();
  const [pendingVerificationEmail, setPendingVerificationEmail] = useState("");
  const [resendStatus, setResendStatus] = useState<"idle" | "sent">("idle");
  const [resendError, setResendError] = useState("");

  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { email: initialEmail, password: "" },
  });
  const currentEmail = form.watch("email")?.trim() ?? "";

  const onSubmit = async (data: FormData) => {
    try {
      await login(data, { returnTo });
    } catch (error) {
      const response = (error as AxiosError<{ detail?: string; title?: string }>).response?.data;
      const detail = response?.detail;
      const title = response?.title;
      const normalizedEmail = data.email.trim().toLowerCase();

      if (title === "EMAIL_NOT_VERIFIED") {
        setPendingVerificationEmail(normalizedEmail);
      } else {
        setPendingVerificationEmail("");
      }

      setResendStatus("idle");
      setResendError("");
      form.setError("root", { message: detail ?? t("errorMessage") });
      form.resetField("password");
    }
  };

  const onResendVerification = async () => {
    if (!pendingVerificationEmail) return;
    try {
      await authApi.resendVerification(pendingVerificationEmail);
      setResendStatus("sent");
      setResendError("");
      startCooldown(60);
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
      const retryAfter = getRetryAfterSeconds(error);
      if (retryAfter) startCooldown(retryAfter);
      setResendError(detail ?? t("resend.error"));
    }
  };

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle className="text-2xl">{t("title")}</CardTitle>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("emailLabel")}</FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      placeholder={t("emailPlaceholder")}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("passwordLabel")}</FormLabel>
                  <FormControl>
                    <Input type="password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {resetSuccess && (
              <p className="text-sm text-green-600">{t("resetSuccessMessage")}</p>
            )}
            {oauthError && (
              <p className="text-sm text-destructive">{decodeURIComponent(oauthError)}</p>
            )}
            {form.formState.errors.root && (
              <p className="text-sm text-destructive">
                {form.formState.errors.root.message}
              </p>
            )}
            {pendingVerificationEmail && (
              <div className="space-y-2 rounded-md border border-border/70 bg-muted/40 p-3">
                <p className="text-sm text-muted-foreground">
                  {t("resend.description", { email: pendingVerificationEmail })}
                </p>
                {resendStatus === "sent" && (
                  <p className="text-sm text-green-600">{t("resend.success")}</p>
                )}
                {resendError && (
                  <p className="text-sm text-destructive">{resendError}</p>
                )}
                <Button
                  type="button"
                  variant="outline"
                  className="w-full"
                  onClick={onResendVerification}
                  disabled={isCoolingDown}
                >
                  {isCoolingDown
                    ? t("resend.cooldown", { seconds: secondsLeft })
                    : t("resend.action")}
                </Button>
              </div>
            )}
            <Button
              type="submit"
              className="w-full"
              disabled={form.formState.isSubmitting}
            >
              {t("submitButton")}
            </Button>
            <SocialLoginButtons returnTo={returnTo} />
            <div className="flex flex-col gap-1 text-center text-sm text-muted-foreground">
              <Link
                href={buildAuthPageHref({
                  locale,
                  page: "signup",
                  returnTo,
                  email: currentEmail,
                })}
                className="hover:underline hover:text-foreground"
              >
                {t("signupLink")}
              </Link>
              <Link
                href={buildAuthPageHref({
                  locale,
                  page: "forgot-password",
                  returnTo,
                  email: currentEmail,
                })}
                className="hover:underline hover:text-foreground"
              >
                {t("forgotPasswordLink")}
              </Link>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
