# Guide: Building the Feed Module From Zero

Listings for needs, offers, and trip availabilities — trait filters and personalized ranking via embeddings.

**Status:** Doc complete · **Backend:** ⬜ not started · **Service:** `FeedService` (planned)

**Depends on:** [user_module.md](./user_module.md), [traits_module.md](./traits_module.md) · **Unblocks:** [seller_profile](./seller_profile_module.md) seller-C, [profile_portfolio](./profile_portfolio_module.md) portfolio-C/D

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 1

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#feed-module-planned--wave-1) · [conventions.md](./conventions.md) · [api_index.md](./api_index.md)

Conventions: thin clients, backend-owned business rules, SQLC, gRPC via `app.App`, **keyset pagination** for infinite scroll.

---

## Goals

| Goal | Detail |
|------|--------|
| **Primary feed** | `ListForYouFeed` — personalized, hybrid-ranked page |
| **Fallback feed** | `ListFeed` — chronological when viewer has no interest profile (cold start) |
| **Filtered feeds** | By trait (`ListFeedByTrait`) and by author (`ListFeedByAuthor`) |
| **Writes** | `CreateFeedItem`, `GetFeedItem` |
| **No N+1** | Fixed ~4–5 SQL round-trips per page regardless of `limit` |
| **Controllable ranking** | Hard SQL filters first; vectors re-rank inside a bounded candidate set |

---

## Architecture Overview

```text
Client (web / android / ios)
        │
        ▼ gRPC
FeedService (internal/service/feed.go)
        │
        ├─ Auth context: viewer_id (AuthService interceptor)
        │
        ├─ ListForYouFeed
        │    ├─ Q1: user_interest_profiles.embedding
        │    ├─ Q2: ranked feed_items (filter + score + keyset) — single query
        │    ├─ Q3: batch traits  (feed_item_id = ANY($ids))
        │    ├─ Q4: batch media   (feed_item_id = ANY($ids))
        │    └─ assemble pb.FeedItem (no DB calls in loop)
        │
        ├─ ListFeed (cold start / explicit chronological)
        │    └─ keyset on (created_at, id)
        │
        └─ CreateFeedItem
             ├─ tx: insert feed_items + traits + media rows
             ├─ compute text embedding (sync)
             └─ queue image embedding (async, optional in early MVP)
```

**Ranking formula (MVP, weights configurable in Go constants or env):**

```text
score =
    w_rel   * (1 - cosine_distance(user_emb, item_emb))
  + w_rec   * recency_decay(created_at)     -- e.g. exp(-age_days / 7)
  + w_trust * normalize(user_stats.implicit_trust_score)
  + w_trait * trait_overlap_bonus           -- 0 or 1 if viewer follows matching trait
```

Hard gates always apply in SQL: `status = active`, `visibility = public`, blocked authors excluded via `user_blocks`.

---

## Step 1: Database Migration

Create `apps/backend/db/migration/000002_feed.up.sql` (and matching `.down.sql`).

### 1.1 Trait taxonomy

Adjacency-list tree shared by products and services (per README).

```sql
CREATE TABLE traits (
    id          bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    parent_id   bigint REFERENCES traits(id) ON DELETE CASCADE,
    slug        varchar(100) NOT NULL,
    label       varchar(200) NOT NULL,
    sort_order  int NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE NULLS NOT DISTINCT (parent_id, slug)
);

CREATE INDEX traits_parent_idx ON traits (parent_id, sort_order);
```

Seed root traits and a small subtree in `apps/backend/db/seed.sql` (e.g. `goods`, `services`, `travel`).

### 1.2 Feed items (source of truth)

Single table for all feed kinds.

```sql
CREATE TABLE feed_items (
    id               bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    author_id        bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind             varchar(20) NOT NULL
                     CHECK (kind IN ('need', 'offer', 'trip')),
    title            varchar(200) NOT NULL,
    body             text,
    status           varchar(20) NOT NULL DEFAULT 'active'
                     CHECK (status IN ('active', 'fulfilled', 'expired', 'removed')),
    visibility       varchar(20) NOT NULL DEFAULT 'public'
                     CHECK (visibility IN ('public', 'followers', 'private')),
    -- trip-specific (nullable for need/offer)
    origin_tag       varchar(100),
    dest_tag         varchar(100),
    available_from   timestamptz,
    available_until  timestamptz,
    -- personalization (text embedding at MVP; image fusion later)
    embedding        vector(768),
    created_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX feed_items_chrono_idx
    ON feed_items (created_at DESC, id DESC)
    WHERE status = 'active' AND visibility = 'public';

CREATE INDEX feed_items_author_idx
    ON feed_items (author_id, created_at DESC, id DESC);

CREATE INDEX feed_items_embedding_hnsw_idx
    ON feed_items USING hnsw (embedding vector_cosine_ops)
    WHERE status = 'active' AND visibility = 'public' AND embedding IS NOT NULL;
```

> **Dimension:** `768` matches common text embedding models (e.g. many sentence-transformers). Pick one model, document it here, and keep dimension consistent across user and item vectors.

### 1.3 Trait and media associations

```sql
CREATE TABLE feed_item_traits (
    feed_item_id bigint NOT NULL REFERENCES feed_items(id) ON DELETE CASCADE,
    trait_id     bigint NOT NULL REFERENCES traits(id) ON DELETE CASCADE,
    PRIMARY KEY (feed_item_id, trait_id)
);

CREATE INDEX feed_item_traits_trait_idx ON feed_item_traits (trait_id, feed_item_id);

CREATE TABLE feed_item_media (
    id           bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    feed_item_id bigint NOT NULL REFERENCES feed_items(id) ON DELETE CASCADE,
    object_key   text NOT NULL,
    sort_order   int NOT NULL DEFAULT 0,
    embedding    vector(768),  -- phase 1b: async after upload
    created_at   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX feed_item_media_item_idx ON feed_item_media (feed_item_id, sort_order);
```

### 1.4 User interest profile

```sql
CREATE TABLE user_trait_follows (
    user_id   bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trait_id  bigint NOT NULL REFERENCES traits(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, trait_id)
);

CREATE TABLE user_interest_profiles (
    user_id      bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    embedding    vector(768),
    source       varchar(50) NOT NULL DEFAULT 'onboarding',
    updated_at   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Cold start:** no row or `embedding IS NULL` → `ListForYouFeed` delegates to `ListFeed`.

Interest vector sources (MVP):

1. **Onboarding** — user selects traits; centroid = average of trait prototype embeddings (store prototypes in `traits` or a `trait_embeddings` table).
2. **Implicit (later)** — rolling average of embeddings from saved/completed items.

### 1.5 Optional read projection (post-MVP optimization)

Defer `feed_item_cards` until join cost is measured. Documented here for when Postgres Q2 joins become hot:

```sql
-- CREATE TABLE feed_item_cards ( ... );  -- see design notes in §8
```

### 1.6 Reuse existing tables

| Table | Use in feed |
|-------|-------------|
| `user_blocks` | `NOT EXISTS` filter on every list query |
| `user_profiles` | Author display in Q2 JOIN or batch fetch |
| `user_stats` | Trust component in score; **read cache only** — never lazy-recalc on feed path |

---

## Step 2: Protocol Buffers

Create `apps/backend/internal/apitypes/` and run `gen-openapi`.

```protobuf
syntax = "proto3";
package zula;

option go_package = "zula/apps/backend/internal/apitypes";
option swift_prefix = "ZLA";

service FeedService {
    rpc ListForYouFeed(ListForYouFeedRequest) returns (ListFeedResponse);
    rpc ListFeed(ListFeedRequest) returns (ListFeedResponse);
    rpc ListFeedByTrait(ListFeedByTraitRequest) returns (ListFeedResponse);
    rpc ListFeedByAuthor(ListFeedByAuthorRequest) returns (ListFeedResponse);
    rpc CreateFeedItem(CreateFeedItemRequest) returns (FeedItem);
    rpc GetFeedItem(GetFeedItemRequest) returns (FeedItem);
    rpc FollowTrait(FollowTraitRequest) returns (FollowTraitResponse);
}

message FeedCursor {
    string anchor_a = 1;  // RFC3339 created_at OR score as string
    string anchor_b = 2;  // snowflake id
    string mode = 3;      // "chrono" | "ranked"
}

message ListForYouFeedRequest {
    FeedCursor cursor = 1;
    int32 limit = 2;
}

message ListFeedRequest {
    FeedCursor cursor = 1;
    int32 limit = 2;
}

message ListFeedByTraitRequest {
    string trait_id = 1;
    FeedCursor cursor = 2;
    int32 limit = 3;
}

message ListFeedByAuthorRequest {
    string author_id = 1;
    FeedCursor cursor = 2;
    int32 limit = 3;
}

message ListFeedResponse {
    repeated FeedItem items = 1;
    FeedCursor next_cursor = 2;
    bool has_more = 3;
}

message FeedItem {
    string id = 1;
    string kind = 2;
    string title = 3;
    string body = 4;
    FeedAuthor author = 5;
    repeated TraitRef traits = 6;
    repeated string media_object_keys = 7;
    string created_at = 8;
    double relevance_score = 9;  // populated for ListForYouFeed
    TripInfo trip = 10;
}

message FeedAuthor {
    string user_id = 1;
    string username = 2;
    string display_name = 3;
    string avatar_url = 4;
    double explicit_rating_avg = 5;
    int32 implicit_trust_score = 6;
}

message TraitRef {
    string id = 1;
    string slug = 2;
    string label = 3;
}

message TripInfo {
    string origin_tag = 1;
    string dest_tag = 2;
    string available_from = 3;
    string available_until = 4;
}

message CreateFeedItemRequest {
    string kind = 1;
    string title = 2;
    string body = 3;
    repeated string trait_ids = 4;
    repeated string media_object_keys = 5;
    TripInfo trip = 6;
}

message GetFeedItemRequest {
    string feed_item_id = 1;
}

message FollowTraitRequest {
    string trait_id = 1;
}

message FollowTraitResponse {
    bool ok = 1;
}
```

Register `FeedService` in `apps/backend/internal/app/app.go`:

```go
a.FeedService = service.NewFeedService(a.Queries, a.DBPool /* + embed client */)
pb.RegisterFeedServiceServer(a.GrpcServer, a.FeedService)
```

---

## Step 3: SQLC Queries

Add `apps/backend/db/query/feed.sql`. Run `sqlc generate` from `apps/backend`.

### 3.1 Chronological list (cold start + `ListFeed`)

```sql
-- name: ListPublicFeed :many
SELECT
    fi.id,
    fi.author_id,
    fi.kind,
    fi.title,
    fi.body,
    fi.origin_tag,
    fi.dest_tag,
    fi.available_from,
    fi.available_until,
    fi.created_at,
    up.display_name,
    up.avatar_url,
    u.username,
    COALESCE(us.explicit_rating_avg, 5.00)::numeric(3,2) AS explicit_rating_avg,
    COALESCE(us.implicit_trust_score, 100)::integer AS implicit_trust_score
FROM feed_items fi
JOIN users u ON u.id = fi.author_id
LEFT JOIN user_profiles up ON up.user_id = fi.author_id
LEFT JOIN user_stats us ON us.user_id = fi.author_id
WHERE fi.status = 'active'
  AND fi.visibility = 'public'
  AND NOT EXISTS (
      SELECT 1 FROM user_blocks ub
      WHERE ub.blocker_id = sqlc.arg(viewer_id)
        AND ub.blocked_id = fi.author_id
  )
  AND (
      sqlc.narg(cursor_created_at)::timestamptz IS NULL
      OR (fi.created_at, fi.id) < (
          sqlc.narg(cursor_created_at)::timestamptz,
          sqlc.narg(cursor_id)::bigint
      )
  )
ORDER BY fi.created_at DESC, fi.id DESC
LIMIT sqlc.arg(page_limit);
```

**Cursor:** `(created_at, id)` — required by AGENTS.md for infinite scroll.

### 3.2 Personalized list (`ListForYouFeed`) — single ranked query

```sql
-- name: ListPersonalizedFeed :many
SELECT
    fi.id,
    fi.author_id,
    fi.kind,
    fi.title,
    fi.body,
    fi.origin_tag,
    fi.dest_tag,
    fi.available_from,
    fi.available_until,
    fi.created_at,
    up.display_name,
    up.avatar_url,
    u.username,
    COALESCE(us.explicit_rating_avg, 5.00)::numeric(3,2) AS explicit_rating_avg,
    COALESCE(us.implicit_trust_score, 100)::integer AS implicit_trust_score,
    (
        sqlc.arg(w_rel)::float8 * (1 - (fi.embedding <=> sqlc.arg(user_embedding)::vector))
      + sqlc.arg(w_rec)::float8 * exp(
            -extract(epoch FROM (now() - fi.created_at)) / 86400.0 / 7.0
        )
      + sqlc.arg(w_trust)::float8 * (COALESCE(us.implicit_trust_score, 100)::float8 / 100.0)
      + sqlc.arg(w_trait)::float8 * CASE
            WHEN EXISTS (
                SELECT 1
                FROM feed_item_traits fit
                JOIN user_trait_follows utf ON utf.trait_id = fit.trait_id
                WHERE fit.feed_item_id = fi.id
                  AND utf.user_id = sqlc.arg(viewer_id)
            ) THEN 1.0 ELSE 0.0
        END
    )::float8 AS score
FROM feed_items fi
JOIN users u ON u.id = fi.author_id
LEFT JOIN user_profiles up ON up.user_id = fi.author_id
LEFT JOIN user_stats us ON us.user_id = fi.author_id
WHERE fi.status = 'active'
  AND fi.visibility = 'public'
  AND fi.embedding IS NOT NULL
  AND fi.created_at > now() - make_interval(days => sqlc.arg(candidate_window_days)::int)
  AND NOT EXISTS (
      SELECT 1 FROM user_blocks ub
      WHERE ub.blocker_id = sqlc.arg(viewer_id)
        AND ub.blocked_id = fi.author_id
  )
  AND (
      sqlc.narg(cursor_score)::float8 IS NULL
      OR (
          (
              sqlc.arg(w_rel)::float8 * (1 - (fi.embedding <=> sqlc.arg(user_embedding)::vector))
            + sqlc.arg(w_rec)::float8 * exp(
                  -extract(epoch FROM (now() - fi.created_at)) / 86400.0 / 7.0
              )
            + sqlc.arg(w_trust)::float8 * (COALESCE(us.implicit_trust_score, 100)::float8 / 100.0)
            + sqlc.arg(w_trait)::float8 * CASE
                  WHEN EXISTS (
                      SELECT 1
                      FROM feed_item_traits fit
                      JOIN user_trait_follows utf ON utf.trait_id = fit.trait_id
                      WHERE fit.feed_item_id = fi.id
                        AND utf.user_id = sqlc.arg(viewer_id)
                  ) THEN 1.0 ELSE 0.0
              END
          ),
          fi.id
      ) < (sqlc.narg(cursor_score)::float8, sqlc.narg(cursor_id)::bigint)
  )
ORDER BY score DESC, fi.id DESC
LIMIT sqlc.arg(page_limit);
```

**Cursor:** `(score, id)` for ranked mode. Recompute the same score expression in the `WHERE` keyset clause (or use a subquery/CTE if SQLC duplication is awkward).

**Candidate window:** default 30 days (`candidate_window_days`) keeps the scan bounded.

### 3.3 Batch enrichment (N+1 prevention)

```sql
-- name: ListTraitsForFeedItems :many
SELECT
    fit.feed_item_id,
    t.id,
    t.slug,
    t.label
FROM feed_item_traits fit
JOIN traits t ON t.id = fit.trait_id
WHERE fit.feed_item_id = ANY(sqlc.arg(item_ids)::bigint[])
ORDER BY fit.feed_item_id, t.sort_order;

-- name: ListMediaForFeedItems :many
SELECT feed_item_id, object_key, sort_order
FROM feed_item_media
WHERE feed_item_id = ANY(sqlc.arg(item_ids)::bigint[])
ORDER BY feed_item_id, sort_order;
```

### 3.4 Writes and interest profile

```sql
-- name: CreateFeedItem :one
-- name: InsertFeedItemTraits :copyfrom  (or bulk INSERT)
-- name: InsertFeedItemMedia :copyfrom
-- name: GetFeedItem :one
-- name: UpdateFeedItemEmbedding :exec
-- name: GetUserInterestEmbedding :one
-- name: UpsertUserInterestProfile :one
-- name: FollowTrait :exec
-- name: ListUserTraitFollows :many
```

---

## Step 4: Go Service (`internal/service/feed.go`)

### 4.0 Cross-module wiring (`app.ModuleAPI`)

FeedService receives `app.ModuleAPI` (or individual interfaces) at construction:

```go
// After CreateFeedItem / status change:
_ = moduleAPI.SellerActivity.SyncSellerActivityStats(ctx, authorID)

// List endpoints — filter blocked authors when viewer authenticated:
block, _ := moduleAPI.Blocks.ResolveViewerBlock(ctx, viewerID, authorID)
if block.EitherBlocked { continue }
```

Register on `app.App` alongside other services. See [architecture.md](./architecture.md#internal-module-api-cross-service).

### 4.1 Query budget (invariant)

Every list endpoint must respect this budget:

| Step | Query | Count |
|------|-------|-------|
| Interest embedding | `GetUserInterestEmbedding` | 0–1 |
| Page rows | `ListPersonalizedFeed` or `ListPublicFeed` | 1 |
| Traits | `ListTraitsForFeedItems` | 1 |
| Media | `ListMediaForFeedItems` | 1 |
| **Total** | | **3–4** |

The assembly loop over rows must **only** read from in-memory maps. **Never** call `GetUserProfile` per author (that triggers lazy trust recalculation and causes N+1-style heavy queries).

Fetch `limit + 1` rows to set `has_more` without a separate `COUNT` query.

### 4.2 `ListForYouFeed` flow

```go
func (s *FeedService) ListForYouFeed(ctx context.Context, req *pb.ListForYouFeedRequest) (*pb.ListFeedResponse, error) {
    viewerID := viewerFromContext(ctx)
    limit := clampLimit(req.Limit)

    emb, err := s.queries.GetUserInterestEmbedding(ctx, viewerID)
    if err != nil || !emb.Valid {
        return s.ListFeed(ctx, &pb.ListFeedRequest{Cursor: req.Cursor, Limit: req.Limit})
    }

    rows, err := s.queries.ListPersonalizedFeed(ctx, /* weights, cursor, limit+1 */)
  // build itemIDs, traitMap, mediaMap via batch queries
    // map to pb.FeedItem slice
    // build next_cursor from last row if has_more
}
```

### 4.3 `CreateFeedItem` flow

```go
func (s *FeedService) CreateFeedItem(ctx context.Context, req *pb.CreateFeedItemRequest) (*pb.FeedItem, error) {
    authorID := viewerFromContext(ctx)
    // validate kind, title, trait_ids exist, trip fields when kind == trip

    tx, err := s.pool.Begin(ctx)
    // defer rollback

    item, err := txQueries.CreateFeedItem(ctx, ...)
    // bulk insert traits + media

    text := req.Title + "\n" + req.Body
    vec, err := s.embedder.EmbedText(ctx, text)
    // UpdateFeedItemEmbedding in same tx

    tx.Commit(ctx)
    return s.GetFeedItem(ctx, &pb.GetFeedItemRequest{FeedItemId: item.ID})
}
```

Image embeddings: update `feed_item_media.embedding` in a background worker after MinIO upload; optional for first milestone if MVP uses text-only vectors.

### 4.4 Embedding client

Add a small interface in `internal/lib/embed` (or similar):

```go
type Embedder interface {
    EmbedText(ctx context.Context, text string) ([]float32, error)
    EmbedImage(ctx context.Context, objectKey string) ([]float32, error)
}
```

MVP can use a local model, external API, or a stub returning deterministic vectors for tests. Document the chosen model and dimension in this file.

### 4.5 Building user interest on onboarding

`FollowTrait` RPC:

1. Insert `user_trait_follows`.
2. Load embeddings for followed traits (prototype or average of sample items).
3. `UpsertUserInterestProfile` with averaged vector.

---

## Step 5: N+1 Checklist

Before merging any feed endpoint, verify:

- [ ] No database calls inside `for _, row := range rows`.
- [ ] Traits loaded with `ANY($ids)`, grouped into `map[int64][]TraitRef`.
- [ ] Media loaded with `ANY($ids)`, grouped into `map[int64][]string`.
- [ ] Author fields come from the main list query JOIN (not per-row profile fetch).
- [ ] `GetUserProfile` / `CalculateUserRatingAvg` never called from feed handlers.
- [ ] Personalized ranking uses **one** `ListPersonalizedFeed` query, not per-item similarity in Go.
- [ ] `CreateFeedItem` uses bulk trait/media insert, not a loop of single inserts (prefer `:copyfrom`).

---

## Step 6: Caching Strategy

| Layer | MVP | Later |
|-------|-----|-------|
| Item/user embeddings | Stored on write in Postgres | Fuse text + image vectors |
| Author trust/rating | JOIN `user_stats` (cached table) | Optional `feed_item_cards` snapshot |
| Feed page cache | None | Postgres projection or Redis if needed |
| Block list | Always fresh in SQL | No cache |

Invalidation is simple at MVP: feed reads hit source tables. Profile changes may show stale avatars until next page fetch; acceptable until `feed_item_cards` is added.

---

## Step 7: Implementation Phases

### Phase A — Schema & tooling

- [ ] Migration `000002_feed.up.sql` + down migration
- [ ] Trait seed data in `seed.sql`
- [ ] `feed` + `gen-openapi`
- [ ] SQLC feed queries + `sqlc generate`
- [ ] Register `FeedService` on `App`

### Phase B — Writes & interest profile

- [ ] `CreateFeedItem` with text embedding
- [ ] `GetFeedItem`
- [ ] `FollowTrait` + `user_interest_profiles` upsert
- [ ] Unit tests: validation, trait linkage, embedding dimension

### Phase C — Reads (MVP = personalized)

- [ ] `ListForYouFeed` with hybrid score + `(score, id)` cursor
- [ ] `ListFeed` chronological fallback
- [ ] Batch trait/media enrichment
- [ ] Integration tests: cold start, pagination, block filtering, no duplicate pages

### Phase D — Filtered lists

- [ ] `ListFeedByTrait` (exact trait; subtree later)
- [ ] `ListFeedByAuthor`

### Phase E — Media & async embeddings

- [ ] MinIO upload path for `media_object_keys`
- [ ] Background worker: `feed_item_media.embedding`
- [ ] Optional: blend image vector into `feed_items.embedding`

### Phase F — Optimization (when metrics justify)

- [ ] `feed_item_cards` projection
- [ ] Trait subtree filter via materialized path
- [ ] `ListSimilarItems` endpoint (query embedding vs item/media vectors)

---

## Step 8: Verification

```bash
# Inside devenv shell

# 1. Apply migration (reset if needed)
rm -rf .devenv/state/postgres   # only when intentionally resetting local DB
devenv test

# 2. Regenerate bindings
gen-openapi
cd apps/backend && sqlc generate

# 3. Seed traits + sample feed items (extend seed.sql)
db-seed

# 4. Backend tests
cd apps/backend && go test ./...

# 5. Manual gRPC smoke test (grpcurl or client)
# - FollowTrait → ListForYouFeed returns ranked items
# - New user with no profile → ListForYouFeed behaves like ListFeed
# - Block author → their items absent from both feeds
```

### Pagination tests to add

| Case | Expected |
|------|----------|
| First page, no cursor | Newest / highest score first |
| Last page | `has_more = false`, empty `next_cursor` |
| Concurrent insert while scrolling | No duplicates or skips (keyset property) |
| `limit + 1` fetch | Correct `has_more` without extra COUNT query |

---

## Step 9: File Checklist

| Path | Purpose |
|------|---------|
| `apps/backend/db/migration/000002_feed.up.sql` | Schema |
| `apps/backend/db/migration/000002_feed.down.sql` | Rollback |
| `apps/backend/db/query/feed.sql` | SQLC source |
| `apps/backend/db/seed.sql` | Traits + sample items |
| `apps/backend/internal/apitypes/` | gRPC API |
| `apps/backend/internal/service/feed.go` | FeedService |
| `apps/backend/tests/feed/` | Tests (not `internal/*_test.go`) |
| `apps/backend/internal/lib/embed/` | Embedding client |
| `apps/backend/internal/app/app.go` | Wire FeedService |

---

## Design Decisions (locked for MVP)

1. **MVP ships `ListForYouFeed`**, not chronological-only with personalization later.
2. **Vectors re-rank inside SQL filters** — not unbounded nearest-neighbor over the full catalog.
3. **Traits are explainable filters**; embeddings handle fuzzy relevance.
4. **Keyset pagination only** — `(created_at, id)` for chrono, `(score, id)` for ranked.
5. **Fixed query budget** — batch `ANY($ids)` for traits and media; no per-item DB calls.
6. **Embeddings on write** for text; async for images.
7. **No Redis** until Postgres profiling shows need.

---

## Open Questions (resolve during Phase A)

| Question | Default for MVP |
|----------|-----------------|
| Embedding model & hosting | Document in PR; stub OK for initial CI |
| Trait prototype vectors | Average of 3–5 seed item embeddings per trait |
| `followers` visibility | Schema present; enforce in Phase D+ when follow graph exists |
| Score weights (`w_rel`, etc.) | Constants in `feed.go`; tune with logging |

---

## Related Documentation

- [user_module.md](./user_module.md) — `user_stats` cache pattern (do not lazy-recalc on feed reads)
- [traits_module.md](./traits_module.md) — trait schema ships in this migration
- [media_module.md](./media_module.md) — Phase feed-E async embeddings
- [conventions.md](./conventions.md) — pagination, tests, markdown
- [README.md](../README.md) — product context
