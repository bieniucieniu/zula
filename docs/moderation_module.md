# Guide: Moderation Module

**Report user/content**, admin review queue, and enforcement — beyond user-initiated **BlockUser** (UserService).

**Status:** Doc complete · **Backend:** ⬜ (blocks only) · **Service:** `ModerationService` (planned)

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

---

## Schema

| Table | Purpose |
|-------|---------|
| `user_reports` | Reporter, target, reason, status |
| `content_reports` | Feed/chat targets |
| `moderation_actions` | Admin audit log |

**Next migration:** `000008_moderation.up.sql` — [schema.md](./schema.md#moderation-module-planned--wave-5)

---

## Proto / RPC surface

| RPC | Auth |
|-----|------|
| `ReportUser` | Auth |
| `ReportFeedItem` | Auth |
| `ListPendingReports` | Admin |
| `ResolveReport` | Admin |

Admin pattern matches `UpdateImplicitTrust` (env allowlist). [api_index.md](./api_index.md)

---

## Auth policy

Reporters cannot target self. Admin RPCs require allowlisted Google ID. [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

### Phase mod-A — Reports

- [ ] `user_reports`, `content_reports`
- [ ] `ReportUser`, `ReportFeedItem`
- [ ] Dedupe: one open report per (reporter, target) per 24h
- [ ] Tests: `apps/backend/tests/moderation/` — cannot report self

### Phase mod-B — Admin review

- [ ] `ListPendingReports`, `ResolveReport`
- [ ] Actions: dismiss, suspend, hide content
- [ ] Feed/chat respect `hidden` flag

### Phase mod-C — Automation (later)

- [ ] Auto-hide after N reports
- [ ] Trust penalties via [trust_events.md](./trust_events.md)

---

## Verification

```bash
devenv test
cd apps/backend && go test ./tests/moderation/...
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `apps/backend/db/migration/000008_moderation.up.sql` | Schema |
| `apps/backend/internal/apitypes/` | RPCs |
| `apps/backend/internal/service/moderation.go` | Handlers |
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
