# Zula documentation index

Onboarding starts here. Module guides follow [MODULE_TEMPLATE.md](./MODULE_TEMPLATE.md).

**New developer path:** [product_vision.md](./product_vision.md) → [architecture.md](./architecture.md) → [implementation_plan.md](./implementation_plan.md) → the module you are building.

---

## Meta documentation

| Doc | Purpose |
|-----|---------|
| [product_vision.md](./product_vision.md) | Starter kit for small companies: auth, profile, clients |
| [architecture.md](./architecture.md) | Ktor `app` / `core` / `features` layout, boundaries, ownership |
| [schema.md](./schema.md) | Canonical database tables by module |
| [api_index.md](./api_index.md) | REST routes (see also `/swagger`) |
| [auth_and_permissions.md](./auth_and_permissions.md) | Public vs authenticated vs admin |
| [conventions.md](./conventions.md) | Pagination, markdown, SQLDelight, OpenAPI, tests, client `features/` layout |
| [secrets.md](./secrets.md) | APP_URL / API_URL, Infisical, K8s routing |
| [trust_events.md](./trust_events.md) | `user_trust_ledger.event_type` catalog |
| [clients.md](./clients.md) | Web + Expo native — `@zula/api` / `@zula/oauth`, sign-in paths |
| [implementation_plan.md](./implementation_plan.md) | What is in the starter kit, and what is next |
| [MODULE_TEMPLATE.md](./MODULE_TEMPLATE.md) | Standard module doc shape |

---

## Starter kit

| Piece | Backend | Doc | Feature |
|-------|---------|-----|---------|
| User & auth | ✅ | [user_module.md](./user_module.md) | `features:auth`, `features:user` |
| Public profile | 🔶 partial (A–B) | [seller_profile_module.md](./seller_profile_module.md) | `features:user` |
| Portfolio | ✅ A–B | [profile_portfolio_module.md](./profile_portfolio_module.md) | `features:user` |
| Media | ✅ A–B + GC | [media_module.md](./media_module.md) | `features:media` |
| Web + native | sign-in on native | [clients.md](./clients.md) | `apps/web`, `apps/native` |

## Not in the kit

Guides below match code still in the repo. They are not the product. See [product_vision.md](./product_vision.md).

| Area | Doc |
|------|-----|
| Feed, traits | [feed_module.md](./feed_module.md), [traits_module.md](./traits_module.md) |
| Groups | [groups_module.md](./groups_module.md) |
| Trade | [trade_module.md](./trade_module.md) |
| Chat | [chat_module.md](./chat_module.md) |
| Geolocation | [geolocation_module.md](./geolocation_module.md) |
| Moderation | [moderation_module.md](./moderation_module.md) |

---

## Feature → doc map

| Kit piece | Owning doc(s) |
|-----------|---------------|
| Product vision | [product_vision.md](./product_vision.md) |
| Thin client, heavy backend | [conventions.md](./conventions.md) |
| Sign-in and identity | [user_module.md](./user_module.md) |
| Public profile, portfolio | [seller_profile_module.md](./seller_profile_module.md), [profile_portfolio_module.md](./profile_portfolio_module.md) |
| Uploads | [media_module.md](./media_module.md) |
| Web + native | [clients.md](./clients.md) |

---

## Keeping docs in sync

After merging backend work:

1. Update **Status** and phase checkboxes in the module doc.
2. Update the status row in this file and [implementation_plan.md](./implementation_plan.md).
3. Add new tables to [schema.md](./schema.md) and routes to [api_index.md](./api_index.md).
4. Regenerate SQLDelight + OpenAPI; regenerate web client when API types change.
