import createMiddleware from "next-intl/middleware";
import { routing } from "./src/i18n/routing";

// Keep middleware focused on locale routing.
// Authenticated area protection happens in the client layout because the
// refresh token is scoped to /api/auth and the access token is in memory.
export default createMiddleware(routing);

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
