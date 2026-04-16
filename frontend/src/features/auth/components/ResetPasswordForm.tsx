"use client";
import { useSearchParams } from "next/navigation";
import { useTranslations, useLocale } from "next-intl";
import { useRouter } from "next/navigation";
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
import { passwordSchema } from "@/shared/lib/validations";

const schema = z
  .object({
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    path: ["confirmPassword"],
    message: "비밀번호가 일치하지 않습니다.",
  });

type FormData = z.infer<typeof schema>;

export default function ResetPasswordForm() {
  const t = useTranslations("auth.resetPassword");
  const locale = useLocale();
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  const onSubmit = async (data: FormData) => {
    if (!token) return;
    try {
      await authApi.confirmPasswordReset(token, data.newPassword);
      router.push(`/${locale}/login?reset=success`);
    } catch (error) {
      const detail = (error as AxiosError<{ detail?: string }>).response?.data
        ?.detail;
      form.setError("root", { message: detail ?? t("errorMessage") });
    }
  };

  if (!token) {
    return (
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          <CardTitle className="text-2xl">{t("invalidTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">{t("invalidDescription")}</p>
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
