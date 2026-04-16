"use client";

import { useState } from "react";
import { useTranslations, useLocale } from "next-intl";
import { useRouter } from "next/navigation";
import { memberApi } from "../api/memberApi";
import { useAuthStore } from "@/features/auth/stores/authStore";
import { Button } from "@/shared/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/shared/components/ui/dialog";

export default function WithdrawSection() {
  const t = useTranslations("member.withdraw");
  const router = useRouter();
  const locale = useLocale();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleWithdraw = async () => {
    setLoading(true);
    setError("");
    try {
      await memberApi.withdraw();
      clearAuth();
      router.replace(`/${locale}/login`);
    } catch {
      setError(t("errorMessage"));
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md">
      {error && <p className="mb-2 text-sm text-destructive">{error}</p>}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger asChild>
          <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive">
            {t("button")}
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("dialogTitle")}</DialogTitle>
            <DialogDescription>{t("dialogDescription")}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="gap-2">
            <Button variant="outline" onClick={() => setOpen(false)} disabled={loading}>
              {t("cancelButton")}
            </Button>
            <Button variant="destructive" onClick={handleWithdraw} disabled={loading}>
              {t("confirmButton")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
