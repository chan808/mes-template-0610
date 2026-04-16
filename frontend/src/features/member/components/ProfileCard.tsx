"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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

const schema = z.object({
  nickname: z.string().max(50).optional(),
});

type FormData = z.infer<typeof schema>;

export default function ProfileCard() {
  const t = useTranslations("member.profile");
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["member", "me"],
    queryFn: () => memberApi.getMyInfo().then((res) => res.data.data!),
  });

  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    values: { nickname: data?.nickname ?? "" },
  });

  const onSubmit = async (formData: FormData) => {
    try {
      const nickname = formData.nickname?.trim() || null;
      await memberApi.updateProfile({ nickname });
      await queryClient.invalidateQueries({ queryKey: ["member", "me"] });
      setEditing(false);
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
      form.setError("root", { message: detail ?? t("errorMessage") });
    }
  };

  if (isLoading || !data) return null;

  const providerLabel =
    data.provider
      ? data.provider.charAt(0) + data.provider.slice(1).toLowerCase()
      : t("providerLocal");

  return (
    <Card className="w-full max-w-md">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{t("title")}</CardTitle>
        {!editing && (
          <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
            {t("editButton")}
          </Button>
        )}
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 이메일 */}
        <div>
          <p className="text-sm text-muted-foreground">{t("emailLabel")}</p>
          <p className="font-medium">{data.email}</p>
        </div>

        {/* 가입 방식 */}
        <div>
          <p className="text-sm text-muted-foreground">{t("providerLabel")}</p>
          <span className="inline-block rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium">
            {providerLabel}
          </span>
        </div>

        {/* 닉네임 — 편집 모드 / 보기 모드 */}
        {editing ? (
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
              <FormField
                control={form.control}
                name="nickname"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("nicknameLabel")}</FormLabel>
                    <FormControl>
                      <Input placeholder={t("nicknamePlaceholder")} {...field} />
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
              <div className="flex gap-2">
                <Button type="submit" size="sm" disabled={form.formState.isSubmitting}>
                  {t("saveButton")}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setEditing(false);
                    form.clearErrors();
                  }}
                >
                  {t("cancelButton")}
                </Button>
              </div>
            </form>
          </Form>
        ) : (
          <div>
            <p className="text-sm text-muted-foreground">{t("nicknameLabel")}</p>
            <p className={data.nickname ? "font-medium" : "text-sm text-muted-foreground italic"}>
              {data.nickname ?? "—"}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
