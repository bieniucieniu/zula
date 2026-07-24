# Zula documentation index

Onboarding starts here. Module guides follow [MODULE_TEMPLATE.md](./MODULE_TEMPLATE.md).

**New developer path:** [architecture.md](./architecture.md) (Ktor layout) → [implementation_plan.md](./implementation_plan.md) → the module you are building.

---

## Meta documentation

| Doc | Purpose |
|-----|---------|
| [architecture.md](./architecture.md) | Ktor `app` / `core` / `features` layout, boundaries, ownership |
| [schema.md](./schema.md) | Canonical database tables by module |
| [api_index.md](./api_index.md) | REST routes (see also `/swagger`) |
| [auth_and_permissions.md](./auth_and_permissions.md) | Public vs authenticated vs admin |
| [conventions.md](./conventions.md) | Pagination, markdown, SQLDelight, OpenAPI, tests |
| [secrets.md](./secrets.md) | APP_URL / API_URL, Infisical, K8s routing |
| [trust_events.md](./trust_events.md) | `user_trust_ledger.event_type` catalog |
| [clients.md](./clients.md) | Web + Expo native — `@zula/api` / `@zula/oauth`, sign-in paths |
| [implementation_plan.md](./implementation_plan.md) | Cross-module waves and exit criteria |
| [MODULE_TEMPLATE.md](./MODULE_TEMPLATE.md) | Standard module doc shape |

---

## Module guides

| Module | Backend | Doc | Feature |
|--------|---------|-----|---------|
| User & auth | ✅ MVP | [user_module.md](./user_module.md) | `features:auth`, `features:user` |
| Seller profile | 🔶 partial (A–B) | [seller_profile_module.md](./seller_profile_module.md) | `features:user` |
| Profile & portfolio | 🔶 partial (A–B) | [profile_portfolio_module.md](./profile_portfolio_module.md) | `features:user` |
| Feed | ⬜ | [feed_module.md](./feed_module.md) | `features:feed` (planned) |
| Traits | ⬜ | [traits_module.md](./traits_module.md) | (SQL only → feed) |
| Media | ⬜ | [media_module.md](./media_module.md) | `features:media` (planned) |
| Geolocation | ⬜ | [geolocation_module.md](./geolocation_module.md) | `features:geolocation` (planned) |
| Trade | ⬜ | [trade_module.md](./trade_module.md) | `features:trade` (planned) |
| Validation | ⬜ | [validation_module.md](./validation_module.md) | `features:validation` (planned) |
| Chat | ⬜ | [chat_module.md](./chat_module.md) | `features:chat` (planned) |
| Moderation | ⬜ (blocks only) | [moderation_module.md](./moderation_module.md) | `features:moderation` (planned) |

**Legend:** ✅ MVP = core shipped · 🔶 partial = some phases done · ⬜ = not started

---

## Feature → doc map

| README feature | Owning doc(s) |
|----------------|---------------|
| Thin-client, heavy-backend | [conventions.md](./conventions.md) |
| Social feed | [feed_module.md](./feed_module.md), [traits_module.md](./traits_module.md) |
| Stateful group chats | [chat_module.md](./chat_module.md) |
| Barter coordination | [trade_module.md](./trade_module.md) |
| Dual trust system | [user_module.md](./user_module.md), [trust_events.md](./trust_events.md), [validation_module.md](./validation_module.md) |
| Network geolocation | [geolocation_module.md](./geolocation_module.md) |
| AI media tagging | [media_module.md](./media_module.md), feed Phase E |
| Hand-to-hand validation | [validation_module.md](./validation_module.md) |
| Public seller profiles | [seller_profile_module.md](./seller_profile_module.md), [profile_portfolio_module.md](./profile_portfolio_module.md) |

---

## Keeping docs in sync

After merging backend work:

1. Update **Status** and phase checkboxes in the module doc.
2. Update the status row in this file and [implementation_plan.md](./implementation_plan.md).
3. Add new tables to [schema.md](./schema.md) and routes to [api_index.md](./api_index.md).
4. Regenerate SQLDelight + OpenAPI; regenerate web client when API types change.
