# Client applications

Zula ships three thin clients. Business rules stay on the backend ([conventions.md](./conventions.md)).

| App | Path | Stack |
|-----|------|-------|
| Web | `apps/web/` | React 19, Vite, TypeScript, nginx (static) |
| Android | `apps/android/` | Kotlin, Jetpack Compose |
| iOS | `apps/ios/` | Swift, SwiftUI |

---

## OpenAPI client generation

Source spec: `packages/openapi/openapi3.yaml` (from `gen-openapi`).

| Target | Command | Output |
|--------|---------|--------|
| Web | `gen-api` | `apps/web/src/api/generated/` (Orval + TanStack Query) |
| iOS | `gen-api-ios` | `apps/ios/ZulaKit/Sources/ZulaOpenAPI/` |
| Android | `gen-api-android` | `apps/android/zula-openapi/` (Retrofit + Moshi) |

Backend request/response types live in `apps/backend/internal/apitypes/` (referenced by swag annotations).

Run `gen-openapi` after backend API changes, then regenerate the clients you touched.

---

## Web API access

- Browser uses **REST JSON** at `/api/v1/*` via Orval fetch client (`VITE_API_URL`, default `/api/v1`).
- Local dev: Vite proxies `/api` → backend `http://127.0.0.1:8080`.
- Production: ingress routes `/api/**` to the backend HTTP server; web nginx serves the SPA at `/`.

Mobile clients use the same REST API at `API_URL` (must be `http(s)://host[:port]/api/v1`).

Contracts: [packages/openapi/](../packages/openapi/).

---

## Client responsibilities

| Concern | Client | Backend |
|---------|--------|---------|
| Markdown display | Parse `source_markdown` → HTML / native | Store markdown only |
| Feed card preview | Truncate markdown locally | Omit body on list responses |
| OAuth UI | Provider SDK / web redirect | Token verify, session JWT |
| Image upload | PUT to presigned URL | Issue URL, validate `object_key` |
| Pagination | Pass opaque cursor from previous response | Keyset SQL |
| Trade / validation UI | Show QR/PIN, scan | State machine, codes |
| Trust display | Render numbers from profile | Compute & cache scores |

---

## Session handling

- JWT lifetime **15 minutes** — refresh via `POST /api/v1/auth/authenticate` or `POST /api/v1/auth/refresh` before expiry.
- Attach `Authorization: Bearer <token>` on REST requests (and WS when chat ships).

---

## Feature rollout by wave

Aligned with [implementation_plan.md](./implementation_plan.md):

| Wave | Web / Android / iOS focus |
|------|---------------------------|
| 1 | Feed tab, seller listings tab |
| 2 | Media upload, location tag display |
| 3 | Trade flow, chat WS, QR/PIN validation |
| 4 | Activity tab, case studies on profile |
| 5 | Report user / content |

Use feature flags per platform as needed; API fields may ship before UI.

---

## Markdown & XSS

Each client **must sanitize** rendered markdown output (web: DOMPurify or equivalent; mobile: safe text attributes).

Backend never accepts HTML blobs in `documents.source`.

---

## Translations

Shared keys: `packages/translations/` — compile with `compile-translations` inside devenv.

---

## Related

- [api_index.md](./api_index.md) — REST route index
- [profile_portfolio_module.md](./profile_portfolio_module.md) — readme/portfolio UI composition
- [feed_module.md](./feed_module.md) — feed client patterns
