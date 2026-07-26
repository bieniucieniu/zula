# Zula development conventions

Cross-cutting rules referenced by all module docs.

---

## Thin clients, heavy backend

- Validation, state machines, and SQL live on the **backend**.
- Clients render UI, parse markdown locally, and call REST/WS.
- Never trust client-computed trust scores, trade states, or pagination cursors without server validation.

---

## Ktor module layout

Follow [architecture.md](./architecture.md#ktor-project-layout):

- **`app`** — composition root (`Application.kt`, Koin bootstrap, route mounting)
- **`core:*`** — database, security, openapi, jobrunr
- **`features:*`** — domain silos with `*Routing.kt`, `*Service.kt`, `*Consumer.kt` / `*Publisher.kt`

---

## Keep `ApplicationCall` out of services

Routing owns HTTP. Services take plain data.

| Layer | Owns | Must not |
|-------|------|----------|
| **`*Routing.kt`** | Parse body/query, cookies, headers, `ApplicationCall` side effects (set/clear cookies, redirects, respond), derive request-scoped values (`publicBaseUrl`, client IP, callback URLs) | Business rules, DB writes |
| **`*Service.kt`** | Domain logic, persistence, token/session issuance with already-resolved args (e.g. `issuer: String`, `clientIp`) | Take `ApplicationCall` / touch cookies / read `call.request` |

**Do:**

```kotlin
// Routing
val session = AuthSessionContext(
    issuer = call.publicBaseUrl(config.appUrl),
    clientIp = call.request.local.remoteHost,
)
val tokens = authService.authenticate(request, session)
call.setAccessCookies(tokens)
call.respond(tokens)
```

**Don't:**

```kotlin
// Service — avoid
fun authenticate(call: ApplicationCall, request: AuthenticateRequest): AuthTokensResponse
```

If a service needs several request-derived values, pass a small data class (e.g. `AuthSessionContext`) instead of the call. Cookie set/read/clear stays in routing (see `features/auth`).

---

## Pagination

**User-facing lists** (feeds, reviews, portfolio, activity, chat history) use **keyset (cursor) pagination**:

- Anchor on UUIDv7 **`id` only**: `id < $cursor_id` with `ORDER BY id DESC`
- Creation time is encoded in the UUIDv7; decode via `Ids.createdAtMillis(id)` — do **not** store a redundant `created_at` on UUID PKs
- Response: `next_cursor`, `has_more`

**Offset pagination** is only for small admin grids with bounded datasets.

### Shared cursor DTOs

| DTO | Package | Used for |
|-----|---------|----------|
| `FeedCursor` | `core:openapi` | Feed lists, author feed, trait feed |
| `ProfileCursor` | `core:openapi` | Reviews, portfolio, public activity |

Do not invent a third cursor shape without updating [api_index.md](./api_index.md) and the Ktor OpenAPI route metadata.

---

## Markdown & rich text

- Backend stores **markdown only** in `documents.source` (`format = 'markdown'`).
- No `rendered_html`, no server-side HTML generation.
- List endpoints omit full bodies; detail/readme endpoints return `source_markdown`.
- Clients parse to HTML (web) or native nodes (mobile) and sanitize output.

See [profile_portfolio_module.md](./profile_portfolio_module.md) for length limits.

---

## Tests

- Kotlin tests live under **`features/{name}/src/test/kotlin/`**, grouped by area.
- Core infra tests under **`core/{name}/src/test/kotlin/`**.
- Integration tests under **`app/src/test/kotlin/`**.
- Do **not** colocate production and test sources.
- After API or SQL changes: regenerate SQLDelight + OpenAPI, then `./gradlew test` before marking a wave done.

---

## Database & migrations

- SQLDelight `.sq` files live in `server/src/main/sqldelight/com/zula/`.
- Schema is applied at startup via SQLDelight `Schema.create` + `ensureUuidV7Defaults` (disable auto-migrate via env `AUTO_MIGRATE=false`).
- Never run destructive SQL outside versioned migration files when those are introduced.

Canonical table list: [schema.md](./schema.md).

---

## Entity IDs

- **Primary keys:** `UUID PRIMARY KEY DEFAULT uuidv7()` on entity tables (PostgreSQL 18+).
- **Default:** omit `id` on `INSERT`; read the assigned value from `RETURNING id` (SQLDelight `insert*` queries).
- **Avoid app-generated ids** (`Ids.next()`, `Uuid.generateV7()`) unless the id must exist before insert (rare).
- **No redundant `created_at`** on UUID PK tables — creation time is in the UUIDv7 timestamp (`Ids.createdAtMillis(id)`).
- **Clients:** never mint entity ids; use ids returned by create endpoints (e.g. `AuthTokensResponse.sessionId`).
- **Pagination cursors:** keyset on UUIDv7 `id` only — see [Pagination](#pagination).

---

## API type workflow

```bash
./gradlew :server:generateSqlDelightInterface   # SQLDelight DAOs
npm run gen-api -w apps/web                      # Orval client (when web ships)
```

- Request/response DTOs: `server/src/main/kotlin/features/*/domain/` and Ktor OpenAPI route metadata.
- Shared types: `RichDocument`, `ProfileCursor`, `FeedCursor` (when feed ships).
- Live spec: `/swagger` on running server.

---

## Auth patterns

- REST: Bearer JWT in `Authorization` header; validated in Ktor `Authentication` plugin (`core:security`).
- **Public routes** listed explicitly in route auth config.
- **Admin routes:** env allowlist (e.g. Google ID for `UpdateImplicitTrust`); same pattern for moderation admin.

Full matrix: [auth_and_permissions.md](./auth_and_permissions.md).

---

## Trust updates

- Immutable log: `user_trust_ledger`
- Cache: `user_stats.implicit_trust_score`
- Writers use UserService helpers; event types catalogued in [trust_events.md](./trust_events.md).

---

## Blocks

- **user** feature owns block/unblock routes.
- **Policy A (seller profile):** seller profile returns `404` when either party blocked the other (authenticated viewer).
- Feed/chat/moderation must filter blocked users when those modules ship.

---

## N+1 rules

| Surface | Rule |
|---------|------|
| Feed cards | JOIN `user_stats` only; no lazy trust recalc per row |
| Profile page | Lazy trust recalc OK (1h TTL) |
| Seller header | Do not embed feed listings — separate author feed route |
| Portfolio grid | Return `summary` + cover; load `RichDocument` on detail only |

---

## Documentation

- Module docs follow [MODULE_TEMPLATE.md](./MODULE_TEMPLATE.md).
- Update [docs/README.md](./README.md) status after shipping phases.
- Definition of Done: [implementation_plan.md](./implementation_plan.md#definition-of-done-per-phase).

---

## Kotlin types: prefer interfaces over inheritance

- **Do not use class inheritance** for app/domain code unless an external library or framework requires it (e.g. Ktor `Application` modules, SQLDelight generated types, test base classes from a test framework).
- **Default to interfaces** for dependency injection and cross-feature boundaries **when a second caller exists**. Do not keep empty `core/contracts/*` stubs ahead of need.
- **Use sealed interfaces** (or sealed classes only when you need restricted concrete subtypes with shared state) for closed sets of variants — e.g. event types, result states, strategy hooks.
- **Prefer composition:** wrap or delegate to collaborators instead of extending a base `*Service` / `*Repository` class.
- **Data classes** stay flat; do not build DTO or domain hierarchies with `open` base classes.

```kotlin
// Prefer
sealed interface TrustEvent { ... }
interface BlockResolver { ... } // add under core/contracts when feed/chat need it

// Avoid (unless a library forces it)
open class BaseService { ... }
class UserService : BaseService()
```

---

When introducing a second feature caller, add a Koin contract interface — see [architecture.md](./architecture.md#cross-feature-internal-api). Do not import sibling feature `*Service.kt` classes.

---

## Document reference rules

See [architecture.md](./architecture.md#document-reference-rules).
