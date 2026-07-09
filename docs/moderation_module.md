# Guide: Moderation Module

**Report user/content**, admin review queue, and enforcement — beyond user-initiated **BlockUser** (UserService).

**Status:** Doc complete · **Backend:** ⬜ (blocks only) · **Feature:** `features:moderation`

**Depends on:** [user_module.md](./user_module.md), [feed_module.md](./feed_module.md), [chat_module.md](./chat_module.md) · **Unblocks:** platform safety at scale

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 5 (`mod-A`, `mod-B`)

**Related:** [architecture.md](./architecture.md) · [trust_events.md](./trust_events.md) · [auth_and_permissions.md](./auth_and_permissions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Report flow** | `ReportUser`, `ReportFeedItem` with reason |
| **Queue** | Admin pending reports |
| **Actions** | Warn, suspend, hide content, cancel trade |
| **Blocks** | UserService keeps `BlockUser`; moderation is platform-side |
| **Audit** | `moderation_actions` log |

---

## Architecture

```text
Reporter ──► ReportUser / ReportFeedItem
                    │
                    ▼
              user_reports / content_reports
                    │
Admin ──► ListPendingReports ──► ResolveReport
                    │
                    ├── trust penalty (trust_events)
                    ├── hide feed item / suspend user
                    └── audit → moderation_actions
```

**Ownership split:** [user_module.md](./user_module.md) — peer blocks. This module — platform reports and admin actions.

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `ModerationRouting.kt` | HTTP paths, OpenAPI metadata |
| Service | `ModerationService.kt` | Reports, admin actions, audit log |
| Integration | `ModerationPublisher.kt` | Content-hide / trust events (planned) |

Register in Koin (`moderationModule`) and mount routes from `Application.kt` via `configureModerationRouting()`. Admin routes use the same JWT + allowlisted Google ID check as `UpdateImplicitTrust` ([auth_and_permissions.md](./auth_and_permissions.md)).

---

## Schema

| Table | Purpose |
|-------|---------|
| `user_reports` | Reporter, target, reason, status |
| `content_reports` | Feed/chat targets |
| `moderation_actions` | Admin audit log |

**Next migration:** `000008_moderation.sql` — [schema.md](./schema.md#moderation-module-planned--wave-5)

---

## REST / OpenAPI surface

| Operation | Auth |
|-----------|------|
| `ReportUser` | Auth |
| `ReportFeedItem` | Auth |
| `ListPendingReports` | Admin |
| `ResolveReport` | Admin |

Admin pattern matches `UpdateImplicitTrust` (env allowlist). [api_index.md](./api_index.md)

---

## Auth policy

Reporters cannot target self. Admin routes require allowlisted Google ID via Ktor `Authentication`. [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

### Phase mod-A — Reports

- [ ] `user_reports`, `content_reports`
- [ ] `ReportUser`, `ReportFeedItem`
- [ ] Dedupe: one open report per (reporter, target) per 24h
- [ ] Koin: `moderationModule` + route mount in `Application.kt`
- [ ] Tests: `features/moderation/src/test/kotlin/` — cannot report self

### Phase mod-B — Admin review

- [ ] `ListPendingReports`, `ResolveReport`
- [ ] Actions: dismiss, suspend, hide content
- [ ] Feed/chat respect `hidden` flag

### Phase mod-C — Automation (later)

- [ ] Auto-hide after N reports
- [ ] Trust penalties via [trust_events.md](./trust_events.md) (`TrustLedgerWriter`)

---

## Verification

```bash
./gradlew :core:database:generateSqlDelightInterface
./gradlew :features:moderation:test
./gradlew test
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `core/database/src/main/resources/db/migration/000008_moderation.sql` | Schema |
| `core/database/src/main/sqldelight/moderation.sq` | SQLDelight queries |
| `core/openapi/src/main/kotlin/dto/` | OpenAPI DTOs |
| `features/moderation/src/main/kotlin/.../ModerationRouting.kt` | HTTP routes |
| `features/moderation/src/main/kotlin/.../ModerationService.kt` | Business logic |
| `features/moderation/src/test/kotlin/...` | Tests |
| `docs/moderation_module.md` | This guide |

---

## Open questions

| Question | MVP default |
|----------|-------------|
| Report reasons enum | Fixed list + free-text optional |
| Suspension duration | Manual admin; no auto-expire MVP |
| Appeal flow | Out of scope |

---

## Related documentation

- [user_module.md](./user_module.md) — `BlockUser`
- [seller_profile_module.md](./seller_profile_module.md) — report link from profile (client)
