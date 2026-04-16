"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { AxiosError } from "axios";
import { memberApi } from "../api/memberApi";
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

const schema = z
  .object({
    currentPassword: z.string().min(1),
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    path: ["confirmPassword"],
    message: "비밀번호가 일치하지 않습니다.",
  });

type FormData = z.infer<typeof schema>;

interface Props {
  /** null이면 로컬 계정, 값이 있으면 소셜 계정 → 컴포넌트 미표시 */
  provider: string | null;
}

export default function ChangePasswordForm({ provider }: Props) {
  const t = useTranslations("member.changePassword");
  const [success, setSuccess] = useState(false);

  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });

  // 소셜 계정은 비밀번호가 없으므로 섹션 자체를 렌더링하지 않음
  if (provider !== null) return null;

  const onSubmit = async (data: FormData) => {
    try {
      await memberApi.changePassword({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });
      setSuccess(true);
      form.reset();
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
      form.setError("root", { message: detail ?? t("errorMessage") });
    }
  };

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>{t("title")}</CardTitle>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="currentPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("currentPasswordLabel")}</FormLabel>
                  <FormControl>
                    <Input type="password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="newPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("newPasswordLabel")}</FormLabel>
                  <FormControl>
                    <Input type="password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="confirmPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("confirmPasswordLabel")}</FormLabel>
                  <FormControl>
                    <Input type="password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {success && (
              <p className="text-sm text-green-600">{t("successMessage")}</p>
            )}
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
