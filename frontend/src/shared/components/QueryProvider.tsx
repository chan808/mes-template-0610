"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

export default function QueryProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  // 각 요청마다 새 QueryClient 인스턴스 — SSR 데이터 오염 방지
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60 * 1000, // 1분: 동일 키 재마운트 시 불필요한 재요청 방지
            retry: 1, // 401/403은 재시도 무의미 — axios interceptor에서 처리
            refetchOnWindowFocus: false, // 탭 전환 시 자동 재조회 비활성화
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
