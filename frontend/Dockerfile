FROM node:22-alpine AS base
RUN npm install -g pnpm@9

FROM base AS deps
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

FROM base AS builder
WORKDIR /app

# NEXT_PUBLIC_* 변수는 빌드 시점에 번들에 삽입됨 (GitHub Actions build-arg로 주입)
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_WS_URL
ARG NEXT_PUBLIC_BACKEND_URL
ARG NEXT_PUBLIC_AUTH_HOME_PATH=/rooms
ARG NEXT_PUBLIC_OAUTH_ENABLED=true
ARG NEXT_PUBLIC_OAUTH_PROVIDERS=google
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL \
    NEXT_PUBLIC_WS_URL=$NEXT_PUBLIC_WS_URL \
    NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL \
    NEXT_PUBLIC_AUTH_HOME_PATH=$NEXT_PUBLIC_AUTH_HOME_PATH \
    NEXT_PUBLIC_OAUTH_ENABLED=$NEXT_PUBLIC_OAUTH_ENABLED \
    NEXT_PUBLIC_OAUTH_PROVIDERS=$NEXT_PUBLIC_OAUTH_PROVIDERS

COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN pnpm build

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
