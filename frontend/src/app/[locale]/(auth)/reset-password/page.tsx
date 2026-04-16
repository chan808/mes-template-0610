import { Suspense } from "react";
import ResetPasswordForm from "@/features/auth/components/ResetPasswordForm";

export default function ResetPasswordPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <Suspense>
        <ResetPasswordForm />
      </Suspense>
    </main>
  );
}
