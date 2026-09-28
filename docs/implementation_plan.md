# Zula — Starter kit plan

What the kit includes, and what is left. Product framing: [product_vision.md](./product_vision.md).

**Related:** [README.md](../README.md) · [docs index](./README.md) · [architecture](./architecture.md) · [conventions](./conventions.md)

---

## In the kit

| Piece | Doc | Status |
|-------|-----|--------|
| Auth, profiles, blocks | [user_module.md](./user_module.md) | Done |
| Public profile | [seller_profile_module.md](./seller_profile_module.md) | Done (page, bio, reviews display) |
| Portfolio, pins, markdown | [profile_portfolio_module.md](./profile_portfolio_module.md) | Done (A–B) |
| Image upload / download | [media_module.md](./media_module.md) | Done (no embeddings) |
| Web shell | [clients.md](./clients.md) | Done |
| Native sign-in | [clients.md](./clients.md) | Done |
| `@zula/api`, `@zula/oauth` | [clients.md](./clients.md) | Done |

## Next

- [ ] Native app past sign-in

---

## Not in the kit

These packages and guides are not the product. Do not treat them as the roadmap.

| Area | Doc |
|------|-----|
| Feed, traits, likes, comments, bookmarks | [feed_module.md](./feed_module.md), [traits_module.md](./traits_module.md) |
| Groups | [groups_module.md](./groups_module.md) |
| Trade | [trade_module.md](./trade_module.md) |
| Chat | [chat_module.md](./chat_module.md) |
| Geolocation | [geolocation_module.md](./geolocation_module.md) |
| Moderation reports | [moderation_module.md](./moderation_module.md) |

---

## Rules

Single source: [conventions.md](./conventions.md).

1. Business rules stay on the backend.
2. Keyset pagination — [api_index.md](./api_index.md).
3. Markdown stored as source only.
4. Tests — see [conventions.md](./conventions.md).
5. Regenerate the web client with `gen:api` when routes change.
6. `./gradlew test` before calling a piece done.
