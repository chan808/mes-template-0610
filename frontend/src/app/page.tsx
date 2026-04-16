import { routing } from "@/i18n/routing";
import { redirect } from "next/navigation";

// 미들웨어가 / → /ko 리다이렉트하지만 폴백으로 명시
export default function RootPage() {
  redirect(`/${routing.defaultLocale}`);
}
