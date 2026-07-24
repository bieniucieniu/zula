# Client applications

Zula ships thin clients. Business rules stay on the backend ([conventions.md](./conventions.md)).

| App | Path | Stack |
|-----|------|-------|
| Web | `apps/web/` | React 19, Vite, TanStack Router/Query, TypeScript |
| Native | `apps/native/` | Expo / React Native, TypeScript |

Shared packages: `@zula/api` (Orval client + mutator), `@zula/oauth` (authorize URL helpers, native sign-in hook, static provider definitions).

Compose (`apps/android/`) and SwiftUI (`apps/ios/`) are not current shipping clients.

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
- Production: ingress routes `/api/**` to the backend; web serves the SPA at `/`.

Native uses the same REST API with bearer tokens (`API_URL` must be `http(s)://host[:port]/api`).

Contracts: Ktor OpenAPI (`/swagger`).

---

## Sign-in paths

| Client | Flow |
|--------|------|
| Web | Backend Google OAuth popup: `/api/auth/oauth/google/start?mode=popup` → server `/api/auth/callback/google` → `postMessage` (`zula.oauth.complete`) or redirect `/oauth/complete` → session cookies |
| Native | Provider SDK / AuthSession (PKCE code) → `POST /api/auth/authenticate` → store access/refresh; bearer on requests |

There is **no** SPA route `/oauth/callback`. Server callback is Ktor-only; SPA finish page is `/oauth/complete`.

---

## Client responsibilities

| Concern | Client | Backend |
|---------|--------|---------|
| Markdown display | Parse `source_markdown` → HTML / native | Store markdown only |
| Feed card preview | Truncate markdown locally | Omit body on list responses |
| Sign-in | See [Sign-in paths](#sign-in-paths) | Verify token / code, issue JWT + session |
| Session refresh | `POST /api/auth/refresh` (cookie or body); web should refresh on 401 | Rotate refresh token, new access JWT |
| Image upload | PUT to presigned URL | Issue URL, validate `object_key` |
| Pagination | Pass opaque cursor from previous response | Keyset SQL on UUIDv7 `id` |
| Trade / validation UI | Show QR/PIN, scan | State machine, codes |
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
- [conventions.md](./conventions.md) — pagination, markdown rules
- [architecture.md](./architecture.md) — Ktor feature layout
- [auth_and_permissions.md](./auth_and_permissions.md) — auth matrix
