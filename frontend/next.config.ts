import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";
import { defaultLocale } from "./src/i18n/config";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  reactCompiler: true,
  turbopack: {
    root: __dirname,
  },
  // 이메일 링크 등 외부 링크는 locale 없이 오므로 기본 locale로 redirect
  // 백엔드 app.base-url은 locale과 무관하게 유지
  async redirects() {
    return [
      {
        source: "/verify-email",
        destination: `/${defaultLocale}/verify-email`,
        permanent: false,
      },
      {
        source: "/reset-password",
        destination: `/${defaultLocale}/reset-password`,
        permanent: false,
      },
      // OAuth2 콜백: 백엔드가 locale 없이 리다이렉트하므로 기본 locale로 포워딩
      {
        source: "/auth/callback",
        destination: `/${defaultLocale}/auth/callback`,
        permanent: false,
      },
    ];
  },
};

export default withNextIntl(nextConfig);
