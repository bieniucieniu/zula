# Zula development conventions

Cross-cutting rules referenced by all module docs. Repository-wide agent rules also live in [AGENTS.md](../AGENTS.md).

---

## Thin clients, heavy backend

- Validation, state machines, and SQL live on the **backend**.
- Clients render UI, parse markdown locally, and call REST/WS.
- Never trust client-computed trust scores, trade states, or pagination cursors without server validation.

---

## Pagination

**User-facing lists** (feeds, reviews, portfolio, activity, chat history) use **keyset (cursor) pagination**:

- Anchor tuple e.g. `(created_at, id) < ($cursor_time, $cursor_id)`
- Stable ordering: always include a unique tie-breaker (`id`)
- Response: `next_cursor`, `has_more`

**Offset pagination** is only for small admin grids with bounded datasets.

### Shared cursor messages

| Message | Package | Used for |
|---------|---------|----------|
| `FeedCursor` | `apitypes` | Feed lists, author feed, trait feed |
| `ProfileCursor` | `apitypes` | Reviews, portfolio, public activity |

Do not invent a third cursor shape without updating [api_index.md](./api_index.md) and `apps/backend/internal/apitypes/`.

---

## Markdown & rich text

- Backend stores **markdown only** in `documents.source` (`format = 'markdown'`).
- No `rendered_html`, no server-side HTML generation.
- List endpoints omit full bodies; detail/readme endpoints return `source_markdown`.
- Clients parse to HTML (web) or native nodes (mobile) and sanitize output.

See [profile_portfolio_module.md](./profile_portfolio_module.md) for length limits.

---

## Tests

- Go tests live under **`apps/backend/tests/`**, grouped by area (e.g. `tests/service/`, `tests/feed/`).
- Do **not** add `*_test.go` under `internal/` production packages.
- After API type or SQL changes: `gen-openapi`, `sqlc generate`, `go test ./tests/...`, then `devenv test` before marking a wave done.

---

## Migrations

- SQL files live in `apps/backend/db/migration/000NNN_*.up.sql` (+ optional `.down.sql`).
- **Backend auto-migrate:** on startup the server applies pending migrations and records them in `schema_migrations`.
- Disable with `AUTO_MIGRATE=false` or the `--no-auto-migrate` CLI flag.
- Legacy databases created before auto-migrate are baselined on first startup (existing schema is detected, only missing migrations run).
- After adding a migration: commit the `.up.sql` file; restart the backend (or run tests against a DB with auto-migrate enabled).
- Never run destructive SQL outside migration files.

Canonical table list: [schema.md](./schema.md).

---

## API type workflow

```bash
# inside devenv shell
gen-openapi        # OpenAPI spec from swag annotations
gen-api            # Web (Orval)
gen-api-android    # Android
gen-api-ios        # iOS
```

- Backend request/response structs: `apps/backend/internal/apitypes/`.
- Shared messages: `RichDocument`, `ProfileCursor`, `FeedCursor` in `apitypes`.
- OpenAPI contract: [packages/openapi/](../packages/openapi/).

---

## Auth patterns

- REST: Bearer JWT in `Authorization` header; validated in HTTP middleware.
- **Public methods** listed explicitly in `unprotectedMethods` (`auth.go`).
- **Admin methods:** env allowlist (e.g. Google ID for `UpdateImplicitTrust`); same pattern for moderation admin RPCs.

Full matrix: [auth_and_permissions.md](./auth_and_permissions.md).

---

## Trust updates

- Immutable log: `user_trust_ledger`
- Cache: `user_stats.implicit_trust_score`
- Writers use UserService helpers; event types catalogued in [trust_events.md](./trust_events.md).

---

## Blocks

- **UserService** owns `BlockUser` / `UnblockUser`.
- **Policy A (seller profile):** `GetSellerProfile` returns `NotFound` when either party blocked the other (authenticated viewer).
- Feed/chat/moderation must filter blocked users when those modules ship.

---

## N+1 rules

| Surface | Rule |
|---------|------|
| Feed cards | JOIN `user_stats` only; no lazy trust recalc per row |
| Profile page | Lazy trust recalc OK (1h TTL) |
| Seller header | Do not embed feed listings — separate `ListFeedByAuthor` |
| Portfolio grid | Return `summary` + cover; load `RichDocument` on detail only |

---

## Documentation

- Module docs follow [MODULE_TEMPLATE.md](./MODULE_TEMPLATE.md).
- Update [docs/README.md](./README.md) status after shipping phases.
- Definition of Done: [implementation_plan.md](./implementation_plan.md#definition-of-done-per-phase) (backend tests + doc status + web smoke for user-facing RPCs).

---

## Cross-module internal API

Use `app.ModuleAPI` — see [architecture.md](./architecture.md#internal-module-api-cross-service). Do not import sibling `internal/service` packages.

---

## Document reference rules

See [architecture.md](./architecture.md#document-reference-rules).
