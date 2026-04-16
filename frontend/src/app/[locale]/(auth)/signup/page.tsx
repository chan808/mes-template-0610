import { Suspense } from "react";
import SignupForm from "@/features/auth/components/SignupForm";

export default function SignupPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <Suspense>
        <SignupForm />
      </Suspense>
    </main>
  );
}
