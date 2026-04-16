"use client";

import { useState } from "react";
import { useTranslations, useLocale } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { AxiosError } from "axios";
import { authApi } from "@/features/auth/api/authApi";
import { useCooldown } from "@/features/auth/hooks/useCooldown";
import { getRetryAfterSeconds } from "@/features/auth/utils/retryAfter";
import { memberApi } from "@/features/member/api/memberApi";
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
import { passwordSchema } from "@/shared/lib/validations";
import {
  buildAuthPageHref,
  normalizeReturnTo,
} from "@/features/auth/utils/navigation";

const schema = z.object({
  email: z.string().email(),
  password: passwordSchema,
});

type FormData = z.infer<typeof schema>;

export default function SignupForm() {
  const t = useTranslations("auth.signup");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const returnTo = normalizeReturnTo(searchParams.get("returnTo"));
  const initialEmail = searchParams.get("email")?.trim() ?? "";
  const { secondsLeft, startCooldown, isCoolingDown } = useCooldown();
  const [submittedEmail, setSubmittedEmail] = useState("");
  const [resendStatus, setResendStatus] = useState<"idle" | "sent">("idle");
  const [resendError, setResendError] = useState("");

  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { email: initialEmail, password: "" },
  });
  const currentEmail = form.watch("email")?.trim() ?? "";

  const onSubmit = async (data: FormData) => {
    try {
      await memberApi.signup(data);
      setSubmittedEmail(data.email.trim().toLowerCase());
      setResendStatus("idle");
      setResendError("");
    } catch (error) {
      const axiosError = error as AxiosError<{ detail?: string }>;
      const message =
        axiosError.response?.data?.detail ?? "회원가입에 실패했습니다.";
      form.setError("root", { message });
    }
  };

  const onResendVerification = async () => {
    if (!submittedEmail) return;
    try {
      await authApi.resendVerification(submittedEmail);
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

  if (submittedEmail) {
    return (
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          <CardTitle className="text-2xl">{t("verifyTitle")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-muted-foreground">
            {t("verifyDescription", { email: submittedEmail })}
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
          <Link
            href={buildAuthPageHref({
              locale,
              page: "login",
              returnTo,
              email: submittedEmail,
            })}
            className="block text-sm text-muted-foreground hover:text-foreground hover:underline"
          >
            {t("loginLink")}
          </Link>
        </CardContent>
      </Card>
    );
  }

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
            {form.formState.errors.root && (
              <p className="text-sm text-destructive">
                {form.formState.errors.root.message}
              </p>
            )}
            <Button
              type="submit"
              className="w-full"
              disabled={form.formState.isSubmitting}
            >
              {t("submitButton")}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              <Link
                href={buildAuthPageHref({
                  locale,
                  page: "login",
                  returnTo,
                  email: currentEmail,
                })}
                className="hover:underline hover:text-foreground"
              >
                {t("loginLink")}
              </Link>
            </p>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
