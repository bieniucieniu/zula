# Guide: Traits Module

Shared **traits tree** for categorizing feed items (goods, services, travel). Schema ships inside the feed migration; trait **REST routes** are owned by FeedService in `features:feed`.

**Status:** Doc complete · **Backend:** ⬜ · **Feature:** `features:feed` (traits schema & SQL; no standalone `features:traits` module)

**Depends on:** — · **Unblocks:** [feed_module.md](./feed_module.md) feed-A

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 1.1 (`traits-A`)

**Related:** [schema.md](./schema.md#traits-module-planned--wave-1) · [architecture.md](./architecture.md) · [conventions.md](./conventions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Trait tree** | Hierarchical `traits` table (`parent_id`, `sort_order`, `slug`) |
| **Seed data** | Roots: `goods`, `services`, `travel` + sample subtree |
| **Linkage** | `feed_item_traits` M:N (feed-owned writes) |
| **Explainability** | SQL filters; not replaced by embeddings alone |

---

## Architecture

```text
traits (tree)
    │
    └── feed_item_traits ←── feed_items
              │
              └── ListFeedByTrait / ranking features (FeedService)
```

**Ownership:** Traits **schema** is documented here; **writes and REST routes** live in [feed_module.md](./feed_module.md). Do not add a standalone TraitsService for MVP.

Feature layout (implemented in `features:feed`):

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `FeedRouting.kt` | Trait list/filter endpoints (feed-owned) |
| Service | `FeedService.kt` | Trait SQL, subtree filters, seed reads |
| Integration | — | No MQ for traits MVP |

---

## Schema

| Table | Purpose |
|-------|---------|
| `traits` | Category tree |
| `feed_item_traits` | M:N item ↔ trait |

**Next migration:** `000002_feed.sql` (combined with feed). See [schema.md](./schema.md#traits-module-planned--wave-1).

---

## REST / OpenAPI surface

No standalone service in MVP. Future feed routes: `FollowTrait`, `UnfollowTrait` — [api_index.md](./api_index.md).

---

## Auth policy

Trait reads are public via feed list endpoints. Follow mutations require auth. Full matrix: [auth_and_permissions.md](./auth_and_permissions.md).

---

## Implementation phases

### Phase traits-A — Schema & seed

- [ ] `traits` + `feed_item_traits` in `000002_feed.sql`
- [ ] Seed in `core/database/src/main/resources/db/seed.sql`
- [ ] SQLDelight: `GetTrait`, `ListTraitsByParent`, `ListTraitsForFeedItems`
- [ ] Tests: `features/feed/src/test/kotlin/.../traits/` — seed integrity, no orphan `parent_id`

### Phase traits-B — Follow & interest (feed-owned)

- [ ] `user_trait_follows`, `user_interest_profiles` (see feed module)
- [ ] Trait prototype embeddings (optional Wave 2)

### Phase traits-C — Subtree filter

- [ ] Materialized path or closure for `ListFeedByTrait` subtree
- [ ] Admin CRUD (back-office only)

---

## Verification

```bash
./gradlew :core:database:generateSqlDelightInterface
db-seed
./gradlew :features:feed:test
./gradlew test
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `core/database/src/main/resources/db/migration/000002_feed.sql` | traits + feed tables |
| `core/database/src/main/sqldelight/traits.sq` | SQLDelight |
| `core/database/src/main/resources/db/seed.sql` | Trait tree |
| `features/feed/src/main/kotlin/.../FeedRouting.kt` | Trait list/filter routes |
| `features/feed/src/main/kotlin/.../FeedService.kt` | Trait SQL + filters |
| `features/feed/src/test/kotlin/.../traits/` | Trait seed/filter tests |
| `docs/traits_module.md` | This guide |

---

## Open questions

| Question | MVP default |
|----------|-------------|
| Closure table vs recursive CTE | Recursive CTE for MVP; optimize if slow |
| Localized trait labels | English slugs only; i18n via translations package later |
| User-created traits | No — admin/seed only |

---

## Related documentation

- [feed_module.md](./feed_module.md) — primary implementation guide
- [implementation_plan.md](./implementation_plan.md) — Wave 1
