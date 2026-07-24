# syntax=docker/dockerfile:1
# Multi-arch: linux/amd64 (x64) + linux/arm64 (Apple Silicon, Raspberry Pi 4 / ARMv8)
#
# Build once on $BUILDPLATFORM (Bun + Vite/Nitro), then copy .output into per-arch runtime.
#
# Layer order (cache-friendly):
#   1) bun install (package manifests + lockfile)
#   2) build @zula/* packages
#   3) build zula-web → .output
#
#   docker build -f docker/web/Dockerfile -t zula-web .

ARG BUN_IMAGE=oven/bun:1.3-alpine

FROM --platform=$BUILDPLATFORM ${BUN_IMAGE} AS build
WORKDIR /workspace

# --- 1. Install deps (invalidates when manifests / lockfile change) ---
COPY package.json bun.lock ./
COPY apps/web/package.json ./apps/web/
COPY packages/api/package.json ./packages/api/
COPY packages/oauth/package.json ./packages/oauth/
RUN bun install --frozen-lockfile --filter zula --filter zula-web --filter '@zula/*'

# --- 2. Build workspace packages (invalidates when package sources change) ---
COPY packages/api/tsconfig.json packages/api/tsdown.config.ts ./packages/api/
COPY packages/api/src ./packages/api/src
COPY packages/oauth/tsconfig.json packages/oauth/tsdown.config.ts ./packages/oauth/
COPY packages/oauth/src ./packages/oauth/src
RUN bun run build:packages

# --- 3. Build web (invalidates when web sources / vite config change) ---
COPY apps/web/tsconfig.json apps/web/vite.config.ts apps/web/components.json ./apps/web/
COPY apps/web/public ./apps/web/public
COPY apps/web/src ./apps/web/src
RUN bun run --filter zula-web build

FROM ${BUN_IMAGE} AS runtime
WORKDIR /app

RUN addgroup -S zula && adduser -S -G zula -H -s /sbin/nologin zula

COPY --from=build --chown=zula:zula /workspace/apps/web/.output ./.output

USER zula
EXPOSE 3000

ENV NODE_ENV=production \
  PORT=3000 \
  HOST=0.0.0.0

ENTRYPOINT ["bun", ".output/server/index.mjs"]
