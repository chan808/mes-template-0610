import { Suspense } from "react";
import OAuthCallbackClient from "@/features/auth/components/OAuthCallbackClient";

export default function OAuthCallbackPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center">
          <p className="text-muted-foreground">Processing login...</p>
        </main>
      }
    >
      <OAuthCallbackClient />
    </Suspense>
  );
}
