import { Suspense } from "react";
import VerifyEmailResult from "@/features/auth/components/VerifyEmailResult";

export default function VerifyEmailPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <Suspense>
        <VerifyEmailResult />
      </Suspense>
    </main>
  );
}
