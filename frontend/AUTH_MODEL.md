# Frontend Auth Model

This starter intentionally defaults to a `client-rendered protected area` model.

## What The Template Supports By Default

- locale-aware public auth pages
- in-memory access token + refresh-token cookie
- client-side refresh on first load of authenticated areas
- redirect to login with preserved `returnTo`
- invitation and deep-link flows that continue after login

The authenticated shell lives in [`src/app/[locale]/(main)/layout.tsx`](src/app/[locale]/(main)/layout.tsx).  
That layout restores auth with `reissue()` on the client and redirects to login when recovery fails.

## What It Does Not Promise By Default

- server component authorization checks for product pages
- SSR-personalized content that is blocked before hydration
- edge or middleware-based auth enforcement using the refresh token

The middleware in this template is for i18n routing only.

## Why This Choice Was Made

- the refresh token is HttpOnly and scoped to `/api/auth`
- the access token lives in memory, not in a cookie
- many invitation-style products only need a solid client-side protected app shell
- this keeps the starter simpler and avoids baking in one SSR auth strategy too early

## When To Upgrade Beyond This Template

Add a stronger server-side auth model when your new project needs:

- SSR-protected dashboards or personalized landing pages
- server components that must know the authenticated member before render
- SEO-sensitive private pages
- authorization checks before hydration for compliance or data exposure reasons

## Practical Upgrade Paths

Common options:

- expose a server-readable session mechanism and authorize in server components
- move refresh/session validation behind a backend-for-frontend layer
- adopt middleware or edge guards only after confirming cookie scope and CSRF strategy

If a project needs those guarantees from day one, treat this starter as the auth domain base, not the final frontend auth architecture.
