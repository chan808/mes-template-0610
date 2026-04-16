# Auth Template

Production-oriented auth starter for projects that need a strong account system from day one.

This repository is intentionally not a minimal boilerplate. It is an opinionated auth starter with:

- email-first accounts
- optional OAuth login
- email verification
- password reset
- JWT access tokens
- Redis-backed refresh sessions
- baseline rate limiting
- observability-ready backend defaults

## Positioning

Use this template when authentication and member management should be part of the real product foundation.

Good fit:

- customer-facing web products
- collaboration tools with invitations and protected deep links
- services that want email login plus optional OAuth convenience
- teams that are comfortable with MySQL and Redis as default infra

Less ideal:

- throwaway prototypes
- projects that want no Redis dependency
- products that want OAuth-only identity and nothing else

## Core Principles

- Email is the primary account identifier.
- OAuth is optional convenience, not the only supported access path.
- Protected routes should preserve `returnTo`.
- Post-login navigation should use `returnTo` first, then the configured authenticated home path.
- Bundled authenticated pages are examples, not mandatory product structure.
- The default frontend auth protection model is client-side for authenticated app areas.

## Included

### Backend

- Kotlin + Spring Boot 4
- Spring Security
- Spring Modulith boundaries
- MySQL + Flyway
- Redis for refresh sessions, verification tokens, password reset tokens, and rate limiting
- email verification and password reset flows
- optional Google / Naver / Kakao OAuth
- Actuator + Micrometer + Prometheus + Grafana
- unit tests and integration tests

### Frontend

- Next.js 16 + React 19
- next-intl
- TanStack Query
- Zustand
- auth pages for login, signup, forgot password, reset password, verify email, and OAuth callback
- `returnTo` support for protected pages and invitation-style deep links
- sample authenticated account pages

## Project Structure

```text
auth-template/
|-- backend/
|   |-- src/main/kotlin
|   |-- src/main/resources
|   |-- src/test/kotlin
|   |-- docker-compose.yml
|   |-- docker-compose.storage.yml
|   |-- .env.example
|   `-- OBSERVABILITY.md
|-- frontend/
|   |-- src
|   |-- messages
|   |-- public
|   |-- .env.example
|   |-- AUTH_MODEL.md
|   `-- README.md
`-- README.md
```

## Quick Start

### 1. Config

- copy `backend/.env.example` to `backend/.env`
- copy `frontend/.env.example` to `frontend/.env.local`

### 2. Infra

```bash
cd backend
docker compose up -d mysql redis
```

Optional observability:

```bash
cd backend
docker compose --profile observability up -d prometheus grafana
```

Optional object storage:

```bash
cd backend
docker compose -f docker-compose.yml -f docker-compose.storage.yml up -d minio
```

### 3. Run

Backend:

```bash
cd backend
./gradlew bootRun
```

Frontend:

```bash
cd frontend
pnpm install
pnpm dev
```

## Verify

Backend unit tests:

```bash
cd backend
./gradlew test
```

Backend integration tests:

```bash
cd backend
./gradlew integrationTest
```

Frontend:

```bash
cd frontend
pnpm build
```

## Notes

- Redis is a core dependency in this starter.
- OAuth is optional, but email login remains the baseline path.
- OAuth and HIBP external lookup can be controlled with explicit feature flags.
- The current OAuth locale and return-to handoff uses the server session during the OAuth round-trip.
- The bundled `dashboard` route is a sample authenticated area and should usually be replaced in a real project.
- Authenticated route protection in the frontend is intentionally CSR-first. Server-component or SSR-protected product pages need extra project-specific work.

## Documents

- [backend/OBSERVABILITY.md](backend/OBSERVABILITY.md)
- [frontend/README.md](frontend/README.md)
- [frontend/AUTH_MODEL.md](frontend/AUTH_MODEL.md)
