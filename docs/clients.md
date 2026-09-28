# Client applications

Zula ships thin clients. Business rules stay on the backend ([conventions.md](./conventions.md)).

| App | Path | Stack |
|-----|------|-------|
| Web | `apps/web/` | React 19, Vite, TanStack Router/Query, TypeScript |
| Native | `apps/native/` | Expo / React Native, TypeScript |

Shared packages: `@zula/api` (Orval client + mutator), `@zula/oauth` (authorize URL helpers, native sign-in hook, static provider definitions).

Compose (`apps/android/`) and SwiftUI (`apps/ios/`) are not current shipping clients.

**Project structure:** feature-first — domain UI under `src/features/{name}/`; shared primitives under `src/components/`. See [conventions.md — Client feature layout](./conventions.md#client-feature-layout-appsweb-appsnative).

---

## OpenAPI client generation

Source spec: Ktor OpenAPI at `/swagger` on a running server (or `server/docs/` export when generated).

| Target | Command | Output |
|--------|---------|--------|
| Shared TS | Orval via `packages/api` | `packages/api/src/generated/` |

Regenerate after backend route/DTO changes.

---

## Web API access

- Browser uses **REST JSON** at `/api/*` via `@zula/api` (cookie session; Vite proxies `/api` → Ktor).
- Realtime: **SSE** (`EventSource` or fetch stream) to `/api/events/stream` — not WebSockets.
- Production: ingress routes `/api/**` to the backend; web serves the SPA at `/`.

Native uses the same REST API with bearer tokens (`API_URL` must be `http(s)://host[:port]/api`). SSE with `Authorization: Bearer …`.

Contracts: Ktor OpenAPI (`/swagger`).

---

## Sign-in paths

| Client | Flow |
|--------|------|
| Web | Same-window Google OAuth redirect: `/api/auth/oauth/google/start` → server `/api/auth/callback/google` → session cookies → redirect `/` |
| Native | Provider SDK / AuthSession (PKCE code) → `POST /api/auth/authenticate` → store access/refresh; bearer on requests |
| Web + Native (local) | When `GET /api/auth/providers` lists `dev`, Dev button uses `clientId` as bypass secret → `POST /api/auth/authenticate` (`provider=dev`) |

There is **no** SPA OAuth callback or finish page. Server callback is Ktor-only and redirects the same window to `/`.

---

## Client responsibilities

| Concern | Client | Backend |
|---------|--------|---------|
| Markdown display | Parse `source_markdown` → HTML / native | Store markdown only |
| Feed card preview | Truncate markdown locally | Omit body on list responses |
| Sign-in | See [Sign-in paths](#sign-in-paths) | Verify token / code, issue JWT + session |
| Session refresh | `POST /api/auth/refresh` (cookie or body); web/native configure `createApiClient({ onUnauthorized })` at boot | Rotate refresh token, new access JWT |
| API client boot | `setDefaultApiClient(createApiClient(...))` from app entry (`__root` / `_layout`) — no import-order globals | — |
| Image upload | `POST /api/media/uploads` (raw body) | Store in MinIO; return `object_key` |
| Pagination | Pass opaque cursor from previous response | Keyset SQL on UUIDv7 `id` |
| Realtime (chat / trade) | Open SSE `GET /api/events/stream`; reconnect with `Last-Event-ID` | Fan-out events; JWT on connect |
| Trade UI | Location mode picker, meetup propose/confirm, complete | State machine, location rules |
| Entity ids | Use ids from API responses only | DB `DEFAULT uuidv7()` + `RETURNING id` |

---

## Markdown rendering

| Platform | Library / approach |
|----------|-------------------|
| Web | `react-markdown` + `remark-gfm` + `rehype-sanitize` (when wired) |
| Native | RN markdown approach when feed ships |

Never expect HTML from the API.

---

## Related

- [api_index.md](./api_index.md) — route list
- [conventions.md](./conventions.md) — pagination, markdown rules, client `features/` layout
- [architecture.md](./architecture.md) — Ktor feature layout
- [auth_and_permissions.md](./auth_and_permissions.md) — auth matrix
