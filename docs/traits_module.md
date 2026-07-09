# Guide: Traits Module

Shared **traits tree** for categorizing feed items (goods, services, travel). Schema ships inside the feed migration; trait **RPCs** are owned by FeedService.

**Status:** Doc complete · **Backend:** ⬜ · **Service:** FeedService (SQL + filters)

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

**Ownership:** Traits **schema** is documented here; **writes and RPCs** live in [feed_module.md](./feed_module.md). Do not add a standalone TraitsService for MVP.

---

## Schema

| Table | Purpose |
|-------|---------|
| `traits` | Category tree |
| `feed_item_traits` | M:N item ↔ trait |

**Next migration:** `000002_feed.up.sql` (combined with feed). See [schema.md](./schema.md#traits-module-planned--wave-1).

---

## Proto / RPC surface

No standalone service in MVP. Future feed RPCs: `FollowTrait`, `UnfollowTrait` — [api_index.md](./api_index.md).

---

## Auth policy

Trait reads are public via feed list endpoints. Follow mutations require auth. Full matrix: [auth_and_permissions.md](./auth_and_permissions.md).

---

## Implementation phases

### Phase traits-A — Schema & seed

- [ ] `traits` + `feed_item_traits` in `000002_feed.up.sql`
- [ ] Seed in `apps/backend/db/seed.sql`
- [ ] SQLC: `GetTrait`, `ListTraitsByParent`, `ListTraitsForFeedItems`
- [ ] Tests: `apps/backend/tests/traits/` — seed integrity, no orphan `parent_id`

### Phase traits-B — Follow & interest (feed-owned)

- [ ] `user_trait_follows`, `user_interest_profiles` (see feed module)
- [ ] Trait prototype embeddings (optional Wave 2)

### Phase traits-C — Subtree filter

- [ ] Materialized path or closure for `ListFeedByTrait` subtree
- [ ] Admin CRUD (back-office only)

---

## Verification

```bash
devenv test
db-seed
cd apps/backend && go test ./tests/traits/...
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `apps/backend/db/migration/000002_feed.up.sql` | traits + feed tables |
| `apps/backend/db/query/traits.sql` | SQLC |
| `apps/backend/db/seed.sql` | Trait tree |
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
