# Client applications

Zula ships three thin clients. Business rules stay on the backend ([conventions.md](./conventions.md)).

| App | Path | Stack |
|-----|------|-------|
| Web | `apps/web/` | React 19, Vite, TypeScript, nginx (static) |
| Android | `apps/android/` | Kotlin, Jetpack Compose |
| iOS | `apps/ios/` | Swift, SwiftUI |

---

## OpenAPI client generation

Source spec: Ktor OpenAPI at `/swagger` on a running server (or `server/docs/` export when generated).

| Target | Command | Output |
|--------|---------|--------|
| Web | `npm run gen-api -w apps/web` | `apps/web/src/api/generated/` (Orval + TanStack Query) |
| iOS | `gen-api-ios` | `apps/ios/ZulaKit/Sources/ZulaOpenAPI/` |
| Android | `gen-api-android` | `apps/android/zula-openapi/` (Retrofit + Moshi) |

Backend request/response DTOs live in `server/src/main/kotlin/features/*/domain/` and Ktor OpenAPI route metadata.

Regenerate clients after backend route/DTO changes.

---

## Web API access

- Browser uses **REST JSON** at `/api/*` via Orval fetch client (`VITE_API_URL`, default `/api`).
- Local dev: Vite proxies `/api`, `/login`, and `/callback` → Ktor server `http://127.0.0.1:8000`.
- Production: ingress routes `/api/**` to the backend HTTP server; web nginx serves the SPA at `/`.

Mobile clients use the same REST API at `API_URL` (must be `http(s)://host[:port]/api`).

Contracts: Ktor OpenAPI (`/swagger`).

---

## Client responsibilities

| Concern | Client | Backend |
|---------|--------|---------|
| Markdown display | Parse `source_markdown` → HTML / native | Store markdown only |
| Feed card preview | Truncate markdown locally | Omit body on list responses |
| Sign-in | OAuth provider SDK → `idToken` → `POST /api/auth/authenticate` | Verify token, DB-assign user/session ids, issue JWT |
| Session refresh | `POST /api/auth/refresh` (cookie or body) | Rotate refresh token, new access JWT |
| Image upload | PUT to presigned URL | Issue URL, validate `object_key` |
| Pagination | Pass opaque cursor from previous response | Keyset SQL on UUIDv7 `id` |
| Trade / validation UI | Show QR/PIN, scan | State machine, codes |
| Entity ids | Use ids from API responses only | DB `DEFAULT uuidv7()` + `RETURNING id` |

---

## Markdown rendering

All three clients parse `source_markdown` locally:

| Platform | Library / approach |
|----------|-------------------|
| Web | `react-markdown` + `remark-gfm` + `rehype-sanitize` |
| Android | Compose `MarkdownText` or similar |
| iOS | `MarkdownUI` or `AttributedString` |

Never expect HTML from the API.

---

## Related

- [api_index.md](./api_index.md) — route list
- [conventions.md](./conventions.md) — pagination, markdown rules
- [architecture.md](./architecture.md) — Ktor feature layout
