"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { AxiosError } from "axios";
import { authApi } from "@/features/auth/api/authApi";
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

const schema = z.object({
  email: z.string().email(),
});

type FormData = z.infer<typeof schema>;

export default function ForgotPasswordForm() {
  const t = useTranslations("auth.forgotPassword");
  const [submitted, setSubmitted] = useState(false);

  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { email: "" },
  });

  const onSubmit = async (data: FormData) => {
    try {
      await authApi.requestPasswordReset(data.email);
    } catch (error) {
      // enumeration attack 방지: 존재하지 않는 이메일도 성공 응답
      // 네트워크 오류 등 명백한 실패만 에러 처리
      const status = (error as AxiosError).response?.status;
      if (status && status !== 404) {
        form.setError("root", { message: t("errorMessage") });
        return;
      }
    }
    // 이메일 존재 여부 노출 방지: 항상 성공 화면 표시
    setSubmitted(true);
  };

  if (submitted) {
    return (
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          <CardTitle className="text-2xl">{t("sentTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">{t("sentDescription")}</p>
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
            <p className="text-sm text-muted-foreground">{t("description")}</p>
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
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
