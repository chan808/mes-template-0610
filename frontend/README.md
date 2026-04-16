# Frontend

Next.js frontend for the auth starter.

## Positioning

This frontend includes the auth flows themselves plus a small sample authenticated area.

- localized auth pages
- OAuth callback handling
- access-token-in-memory + refresh-token-cookie flow
- automatic token reissue on 401
- protected route redirect with preserved `returnTo`
- sample authenticated account workspace at `/{locale}/dashboard`

## Auth Protection Model

This starter is intentionally `CSR-first` for authenticated product areas.

- `middleware.ts` only handles locale routing
- authenticated app protection happens in [`src/app/[locale]/(main)/layout.tsx`](src/app/[locale]/(main)/layout.tsx)
- that layout restores auth on the client by calling `reissue()`
- if recovery fails, the user is redirected to login with `returnTo`

If a new project needs SSR or server-component authorization before hydration, treat that as an extension to add at project start, not something this template already guarantees. See [AUTH_MODEL.md](./AUTH_MODEL.md).

## Important Defaults

- `returnTo` is preserved when a protected route redirects to login
- email login and OAuth callback follow the same post-login redirect rule
- when `returnTo` is missing, the frontend uses `NEXT_PUBLIC_AUTH_HOME_PATH`
- when that variable is missing, fallback is `/{locale}/dashboard`
- OAuth UI can be disabled with `NEXT_PUBLIC_OAUTH_ENABLED=false`

## Environment

Copy `.env.example` to `.env.local`.

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_OAUTH_ENABLED=true
NEXT_PUBLIC_OAUTH_PROVIDERS=google
NEXT_PUBLIC_APP_NAME=Application
NEXT_PUBLIC_DEFAULT_LOCALE=ko
NEXT_PUBLIC_AUTH_HOME_PATH=/dashboard
```

## Structure

```text
src/
|-- app/
|   |-- [locale]/
|   |   |-- (auth)/
|   |   |-- (main)/
|   |   `-- auth/callback/
|-- features/
|   |-- auth/
|   |-- examples/
|   `-- member/
|-- shared/
`-- i18n/
```

## Customizing For A New Project

Usually you should:

- keep the auth pages and shared auth utilities
- replace the sample dashboard with your own authenticated home
- update `NEXT_PUBLIC_AUTH_HOME_PATH`
- keep `returnTo` support for invitations and deep links
- decide early whether your project stays with the default CSR auth shell or needs SSR-protected routes

## Verify

```bash
pnpm build
```
