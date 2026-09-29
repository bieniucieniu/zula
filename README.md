# zula WIP

Starter kit for small-company apps.

Sign-in, a public company profile, and web + mobile shells on one API. Build the company product on this base.

Monorepo: Ktor API (`server/`) · React web (`apps/web/`) · Expo (`apps/native/`) · shared `@zula/api` and `@zula/oauth`.

Detail: [docs/product_vision.md](./docs/product_vision.md).
Deployment: [bieniucieniu/infra](https://github.com/bieniucieniu/infra/tree/main/clusters/rpi/apps/zula)

## Done

- [x] Auth — Google, Apple, session cookies (web), bearer tokens (native), local dev bypass
- [x] Profile — public page, bio, portfolio, pins
- [x] Media — image upload and download
- [x] Web app shell
- [x] Native sign-in
- [x] Shared API client

## Next

- [ ] Native app past sign-in

Rollout: [docs/implementation_plan.md](./docs/implementation_plan.md).

## Run

```bash
devenv --profile web up      # Postgres + API :8000 + web
devenv --profile native up   # Postgres + API + Expo
devenv --profile backend up  # Postgres + API only
```

API routes live under `/api`. Web proxies `/api` to Ktor.

```bash
./gradlew :server:run
bun run web
bun run native
```

## Docs

[docs/README.md](./docs/README.md) → [product vision](./docs/product_vision.md) → [architecture](./docs/architecture.md) → [implementation plan](./docs/implementation_plan.md).
