# Guide: Building the Feed Module From Zero

Listings for needs, offers, and trip availabilities — trait filters and personalized ranking via embeddings.

**Status:** Doc complete · **Backend:** ⬜ not started · **Feature:** `features:feed`

**Depends on:** [user_module.md](./user_module.md), [traits_module.md](./traits_module.md) · **Unblocks:** [seller_profile](./seller_profile_module.md) seller-C, [profile_portfolio](./profile_portfolio_module.md) portfolio-C/D

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 1

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#feed-module-planned--wave-1) · [conventions.md](./conventions.md) · [api_index.md](./api_index.md)

Conventions: thin clients, backend-owned business rules, SQLDelight, REST via Ktor, **keyset pagination** for infinite scroll.

---

## Goals

| Goal | Detail |
|------|--------|
| **Primary feed** | `GET /api/v1/feed/for-you` — personalized, hybrid-ranked page |
| **Fallback feed** | `GET /api/v1/feed` — chronological when viewer has no interest profile (cold start) |
| **Filtered feeds** | By trait (`GET /api/v1/feed/by-trait/{traitId}`) and by author (`GET /api/v1/feed/by-author/{authorId}`) |
| **Writes** | `POST /api/v1/feed/items`, `GET /api/v1/feed/items/{id}` |
| **No N+1** | Fixed ~4–5 SQL round-trips per page regardless of `limit` |
| **Controllable ranking** | Hard SQL filters first; vectors re-rank inside a bounded candidate set |

---

## Architecture Overview

```text
Client (web / android / ios)
        │
        ▼ REST
FeedRouting.kt (features/feed)
        │
        ▼
FeedService.kt
        │
        ├─ Auth context: viewer_id (core:security JWT)
        │
        ├─ GET /feed/for-you
        │    ├─ Q1: user_interest_profiles.embedding
        │    ├─ Q2: ranked feed_items (filter + score + keyset) — single query
        │    ├─ Q3: batch traits  (feed_item_id IN ?)
        │    ├─ Q4: batch media   (feed_item_id IN ?)
        │    └─ assemble FeedItem DTO (no DB calls in loop)
        │
        ├─ GET /feed (cold start / explicit chronological)
        │    └─ keyset on (created_at, id)
        │
        └─ POST /feed/items
             ├─ tx: insert feed_items + traits + media rows
             ├─ compute text embedding (sync)
             ├─ FeedPublisher.kt → feed.item.created (RabbitMQ)
             └─ queue image embedding (async via FeedConsumer.kt, optional in early MVP)
```

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `FeedRouting.kt` | HTTP paths, request parsing, OpenAPI metadata |
| Service | `FeedService.kt` | Ranking SQL, trait batching, block filtering, transactions |
| Publisher | `FeedPublisher.kt` | Outbound `feed.item.created` events |
| Consumer | `FeedConsumer.kt` | Inbound `media.embedding.completed` → update item/media vectors |

**Ranking formula (MVP, weights configurable in Kotlin constants or env):**

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

Create `core/database/src/main/resources/db/migration/000002_feed.sql` (and matching `_rollback.sql`).

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

Seed root traits and a small subtree in `core/database/src/main/resources/db/seed.sql` (e.g. `goods`, `services`, `travel`).

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

**Cold start:** no row or `embedding IS NULL` → `GET /feed/for-you` delegates to `GET /feed`.

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

## Step 2: REST / OpenAPI Surface

Add route summaries to `core/openapi/src/main/resources/openapi.yaml` and DTOs under `core/openapi/src/main/kotlin/.../dto/feed/`. Run `./gradlew :core:openapi:build`.

### Route summary

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/api/v1/feed/for-you` | Auth | Personalized hybrid-ranked page |
| `GET` | `/api/v1/feed` | Public | Chronological feed (cold start fallback) |
| `GET` | `/api/v1/feed/by-trait/{traitId}` | Public | Filter by trait |
| `GET` | `/api/v1/feed/by-author/{authorId}` | Public | Author listings tab |
| `POST` | `/api/v1/feed/items` | Auth | Create listing |
| `GET` | `/api/v1/feed/items/{id}` | Public | Single item detail |
| `POST` | `/api/v1/feed/traits/{traitId}/follow` | Auth | Follow trait (interest profile) |
| `DELETE` | `/api/v1/feed/traits/{traitId}/follow` | Auth | Unfollow trait |

Full auth matrix: [auth_and_permissions.md](./auth_and_permissions.md) · [api_index.md](./api_index.md)

### Kotlin DTOs (request/response shapes)

```kotlin
// core/openapi/.../dto/feed/FeedCursor.kt
data class FeedCursor(
    val anchorA: String,   // RFC3339 created_at OR score as string
    val anchorB: String,   // snowflake id
    val mode: String,      // "chrono" | "ranked"
)

// core/openapi/.../dto/feed/FeedDtos.kt
data class ListFeedQuery(
    val cursor: FeedCursor? = null,
    val limit: Int = 20,
)

data class ListFeedByTraitQuery(
    val cursor: FeedCursor? = null,
    val limit: Int = 20,
)

data class ListFeedByAuthorQuery(
    val cursor: FeedCursor? = null,
    val limit: Int = 20,
)

data class ListFeedResponse(
    val items: List<FeedItem>,
    val nextCursor: FeedCursor?,
    val hasMore: Boolean,
)

data class FeedItem(
    val id: String,
    val kind: String,
    val title: String,
    val body: String?,
    val author: FeedAuthor,
    val traits: List<TraitRef>,
    val mediaObjectKeys: List<String>,
    val createdAt: String,
    val relevanceScore: Double? = null,  // populated for /feed/for-you
    val trip: TripInfo? = null,
)

data class FeedAuthor(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val explicitRatingAvg: Double,
    val implicitTrustScore: Int,
)

data class TraitRef(
    val id: String,
    val slug: String,
    val label: String,
)

data class TripInfo(
    val originTag: String?,
    val destTag: String?,
    val availableFrom: String?,
    val availableUntil: String?,
)

data class CreateFeedItemRequest(
    val kind: String,
    val title: String,
    val body: String?,
    val traitIds: List<String>,
    val mediaObjectKeys: List<String> = emptyList(),
    val trip: TripInfo? = null,
)

data class FollowTraitResponse(
    val ok: Boolean = true,
)
```

Register the feed feature in `app/src/main/kotlin/.../Application.kt` via Koin — not gRPC:

```kotlin
// app/src/main/kotlin/.../Application.kt
install(Koin) {
    modules(
        databaseModule,
        securityModule,
        rabbitmqModule,
        authModule,
        userModule,
        feedModule,   // features/feed/di/feedModule.kt
    )
}

fun Application.configureRouting() {
    val feedRouting: FeedRouting by inject()
    feedRouting.register(this)
}

fun Application.configureRabbitMqConsumers() {
    val feedConsumer: FeedConsumer by inject()
    feedConsumer.start()
}
```

```kotlin
// features/feed/di/feedModule.kt
val feedModule = module {
    single { FeedService(get(), get(), get(), get(), get()) }
    single { FeedRouting(get()) }
    single { FeedPublisher(get()) }
    single { FeedConsumer(get(), get()) }
}
```

---

## Step 3: SQLDelight Queries

Add `core/database/src/main/sqldelight/feed.sq`. Run `./gradlew :core:database:generateSqlDelightInterface`.

> **Parameter style:** SQLDelight uses positional `?` or named `:param`. Nullable cursor fields are bound as Kotlin nullable types; when duplication in the ranked keyset clause is awkward, extract a CTE or use two query variants (first page vs paginated).

### 3.1 Chronological list (cold start + `GET /feed`)

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
      WHERE ub.blocker_id = :viewer_id
        AND ub.blocked_id = fi.author_id
  )
  AND (
      :cursor_created_at IS NULL
      OR (fi.created_at, fi.id) < (
          :cursor_created_at,
          :cursor_id
      )
  )
ORDER BY fi.created_at DESC, fi.id DESC
LIMIT :page_limit;
```

**Cursor:** `(created_at, id)` — required by [conventions.md](./conventions.md) for infinite scroll.

**SQLDelight equivalent:** bind `:cursor_created_at` and `:cursor_id` as nullable; pass `null` on first page.

### 3.2 Personalized list (`GET /feed/for-you`) — single ranked query

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
        :w_rel * (1 - (fi.embedding <=> :user_embedding))
      + :w_rec * exp(
            -extract(epoch FROM (now() - fi.created_at)) / 86400.0 / 7.0
        )
      + :w_trust * (COALESCE(us.implicit_trust_score, 100)::float8 / 100.0)
      + :w_trait * CASE
            WHEN EXISTS (
                SELECT 1
                FROM feed_item_traits fit
                JOIN user_trait_follows utf ON utf.trait_id = fit.trait_id
                WHERE fit.feed_item_id = fi.id
                  AND utf.user_id = :viewer_id
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
  AND fi.created_at > now() - make_interval(days => :candidate_window_days)
  AND NOT EXISTS (
      SELECT 1 FROM user_blocks ub
      WHERE ub.blocker_id = :viewer_id
        AND ub.blocked_id = fi.author_id
  )
  AND (
      :cursor_score IS NULL
      OR (
          (
              :w_rel * (1 - (fi.embedding <=> :user_embedding))
            + :w_rec * exp(
                  -extract(epoch FROM (now() - fi.created_at)) / 86400.0 / 7.0
              )
            + :w_trust * (COALESCE(us.implicit_trust_score, 100)::float8 / 100.0)
            + :w_trait * CASE
                  WHEN EXISTS (
                      SELECT 1
                      FROM feed_item_traits fit
                      JOIN user_trait_follows utf ON utf.trait_id = fit.trait_id
                      WHERE fit.feed_item_id = fi.id
                        AND utf.user_id = :viewer_id
                  ) THEN 1.0 ELSE 0.0
              END
          ),
          fi.id
      ) < (:cursor_score, :cursor_id)
  )
ORDER BY score DESC, fi.id DESC
LIMIT :page_limit;
```

**Cursor:** `(score, id)` for ranked mode. Recompute the same score expression in the `WHERE` keyset clause (or use a subquery/CTE if SQLDelight duplication is awkward).

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
WHERE fit.feed_item_id IN ?
ORDER BY fit.feed_item_id, t.sort_order;

-- name: ListMediaForFeedItems :many
SELECT feed_item_id, object_key, sort_order
FROM feed_item_media
WHERE feed_item_id IN ?
ORDER BY feed_item_id, sort_order;
```

**SQLDelight equivalent:** pass `item_ids` as a bound `List<Long>` for `IN ?`.

### 3.4 Writes and interest profile

```sql
-- name: CreateFeedItem :one
-- name: InsertFeedItemTraits :executemany  (or bulk INSERT)
-- name: InsertFeedItemMedia :executemany
-- name: GetFeedItem :one
-- name: UpdateFeedItemEmbedding :execute
-- name: GetUserInterestEmbedding :one
-- name: UpsertUserInterestProfile :one
-- name: FollowTrait :execute
-- name: ListUserTraitFollows :many
```

---

## Step 4: Kotlin Service (`features/feed/.../FeedService.kt`)

### 4.0 Cross-feature wiring (Koin contract interfaces)

`FeedService` receives Koin contract interfaces at construction — do not import sibling feature packages directly. See [architecture.md](./architecture.md#cross-feature-internal-api).

```kotlin
class FeedService(
    private val database: Database,
    private val queries: FeedQueries,
    private val embedder: Embedder,
    private val blockResolver: BlockResolver,
    private val sellerActivityWriter: SellerActivityWriter,
) {
    // After CreateFeedItem / status change:
    suspend fun onFeedItemWritten(authorId: Long) {
        sellerActivityWriter.syncSellerActivityStats(authorId)
    }

    // List endpoints — filter blocked authors when viewer authenticated:
    suspend fun isBlocked(viewerId: Long, authorId: Long): Boolean {
        return blockResolver.resolveViewerBlock(viewerId, authorId).eitherBlocked
    }
}
```

Register contract bindings in `app` (or `core/contracts`):

```kotlin
single<BlockResolver> { get<UserService>() }
single<SellerActivityWriter> { get<UserService>() }
```

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

Fetch `limit + 1` rows to set `hasMore` without a separate `COUNT` query.

### 4.2 `listForYouFeed` flow

```kotlin
suspend fun listForYouFeed(
    viewerId: Long,
    cursor: FeedCursor?,
    limit: Int,
): ListFeedResponse {
    val pageLimit = clampLimit(limit)

    val embedding = queries.getUserInterestEmbedding(viewerId).executeAsOneOrNull()
    if (embedding?.embedding == null) {
        return listFeed(viewerId, cursor, pageLimit)
    }

    val rows = queries.listPersonalizedFeed(
        viewerId = viewerId,
        userEmbedding = embedding.embedding,
        wRel = RankingWeights.W_REL,
        wRec = RankingWeights.W_REC,
        wTrust = RankingWeights.W_TRUST,
        wTrait = RankingWeights.W_TRAIT,
        candidateWindowDays = 30,
        cursorScore = cursor?.takeIf { it.mode == "ranked" }?.anchorA?.toDoubleOrNull(),
        cursorId = cursor?.anchorB?.toLongOrNull(),
        pageLimit = pageLimit + 1,
    ).executeAsList()

    val (page, hasMore) = trimPage(rows, pageLimit)
    val itemIds = page.map { it.id }
    val traitMap = batchTraits(itemIds)
    val mediaMap = batchMedia(itemIds)

    return ListFeedResponse(
        items = page.map { row -> toFeedItem(row, traitMap, mediaMap) },
        nextCursor = buildRankedCursor(page, hasMore),
        hasMore = hasMore,
    )
}
```

### 4.3 `createFeedItem` flow

```kotlin
suspend fun createFeedItem(
    authorId: Long,
    req: CreateFeedItemRequest,
): FeedItem {
    // validate kind, title, trait_ids exist, trip fields when kind == trip

    return database.transaction {
        val item = queries.createFeedItem(/* ... */).executeAsOne()
        queries.insertFeedItemTraits(/* bulk */)
        queries.insertFeedItemMedia(/* bulk */)

        val text = "${req.title}\n${req.body.orEmpty()}"
        val vec = embedder.embedText(text)
        queries.updateFeedItemEmbedding(item.id, vec)

        item
    }.let { item ->
        sellerActivityWriter.syncSellerActivityStats(authorId)
        feedPublisher.publishFeedItemCreated(item.id)
        getFeedItem(item.id)
    }
}
```

Image embeddings: `FeedConsumer` handles `media.embedding.completed` and updates `feed_item_media.embedding`; optional for first milestone if MVP uses text-only vectors.

### 4.4 Embedding client

Add a small interface in `core/embed/` (or `features/feed/embed/`):

```kotlin
interface Embedder {
    suspend fun embedText(text: String): FloatArray
    suspend fun embedImage(objectKey: String): FloatArray
}
```

MVP can use a local model, external API, or a stub returning deterministic vectors for tests. Document the chosen model and dimension in this file.

### 4.5 RabbitMQ integration

**FeedPublisher.kt** — after successful create/status change:

```kotlin
class FeedPublisher(private val channel: Channel) {
    fun publishFeedItemCreated(feedItemId: Long) {
        channel.basicPublish("feed", "feed.item.created", /* body */)
    }
}
```

**FeedConsumer.kt** — async media embedding completion:

```kotlin
class FeedConsumer(
    private val channel: Channel,
    private val feedService: FeedService,
) {
    fun start() {
        channel.basicConsume("media.embedding.completed") { _, body ->
            // parse event → feedService.updateMediaEmbedding(...)
        }
    }
}
```

### 4.6 Building user interest on onboarding

`POST /feed/traits/{traitId}/follow`:

1. Insert `user_trait_follows`.
2. Load embeddings for followed traits (prototype or average of sample items).
3. `UpsertUserInterestProfile` with averaged vector.

---

## Step 5: N+1 Checklist

Before merging any feed endpoint, verify:

- [ ] No database calls inside `for (row in rows)`.
- [ ] Traits loaded with `IN ?`, grouped into `Map<Long, List<TraitRef>>`.
- [ ] Media loaded with `IN ?`, grouped into `Map<Long, List<String>>`.
- [ ] Author fields come from the main list query JOIN (not per-row profile fetch).
- [ ] `GetUserProfile` / `CalculateUserRatingAvg` never called from feed handlers.
- [ ] Personalized ranking uses **one** `ListPersonalizedFeed` query, not per-item similarity in Kotlin.
- [ ] `CreateFeedItem` uses bulk trait/media insert, not a loop of single inserts (prefer `:executemany`).

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

### Phase feed-A — Schema & tooling

- [ ] Migration `000002_feed.sql` + down migration
- [ ] Trait seed data in `seed.sql`
- [ ] OpenAPI feed routes + `./gradlew :core:openapi:build`
- [ ] SQLDelight feed queries + `./gradlew :core:database:generateSqlDelightInterface`
- [ ] Register `feedModule` in `Application.kt`

### Phase feed-B — Writes & interest profile

- [ ] `POST /feed/items` with text embedding
- [ ] `GET /feed/items/{id}`
- [ ] `POST /feed/traits/{traitId}/follow` + `user_interest_profiles` upsert
- [ ] Unit tests: validation, trait linkage, embedding dimension

### Phase feed-C — Reads (MVP = personalized)

- [ ] `GET /feed/for-you` with hybrid score + `(score, id)` cursor
- [ ] `GET /feed` chronological fallback
- [ ] Batch trait/media enrichment
- [ ] Integration tests: cold start, pagination, block filtering, no duplicate pages

### Phase feed-D — Filtered lists

- [ ] `GET /feed/by-trait/{traitId}` (exact trait; subtree later)
- [ ] `GET /feed/by-author/{authorId}`

### Phase feed-E — Media & async embeddings

- [ ] MinIO upload path for `media_object_keys`
- [ ] `FeedConsumer`: `media.embedding.completed` → `feed_item_media.embedding`
- [ ] Optional: blend image vector into `feed_items.embedding`

### Phase feed-F — Optimization (when metrics justify)

- [ ] `feed_item_cards` projection
- [ ] Trait subtree filter via materialized path
- [ ] `GET /feed/similar/{id}` (query embedding vs item/media vectors)

---

## Step 8: Verification

```bash
# 1. Apply migration (auto-migrate on server start, or run migration runner)
./gradlew test

# 2. Regenerate bindings
./gradlew :core:database:generateSqlDelightInterface
./gradlew :core:openapi:build

# 3. Seed traits + sample feed items (extend core/database/.../seed.sql)

# 4. Backend tests
./gradlew :features:feed:test
./gradlew test

# 5. Manual REST smoke test (curl or HTTP client)
# - POST /feed/traits/{id}/follow → GET /feed/for-you returns ranked items
# - New user with no profile → GET /feed/for-you behaves like GET /feed
# - Block author → their items absent from both feeds
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/feed/for-you?limit=20"
```

### Pagination tests to add

| Case | Expected |
|------|----------|
| First page, no cursor | Newest / highest score first |
| Last page | `hasMore = false`, empty `nextCursor` |
| Concurrent insert while scrolling | No duplicates or skips (keyset property) |
| `limit + 1` fetch | Correct `hasMore` without extra COUNT query |

---

## Step 9: File Checklist

| Path | Purpose |
|------|---------|
| `core/database/src/main/resources/db/migration/000002_feed.sql` | Schema |
| `core/database/src/main/resources/db/migration/000002_feed_rollback.sql` | Rollback |
| `core/database/src/main/sqldelight/feed.sq` | SQLDelight source |
| `core/database/src/main/resources/db/seed.sql` | Traits + sample items |
| `core/openapi/src/main/resources/openapi.yaml` | REST route contract |
| `core/openapi/src/main/kotlin/.../dto/feed/` | Feed DTOs |
| `features/feed/src/main/kotlin/.../FeedRouting.kt` | HTTP routes |
| `features/feed/src/main/kotlin/.../FeedService.kt` | Business logic |
| `features/feed/src/main/kotlin/.../FeedPublisher.kt` | RabbitMQ publish |
| `features/feed/src/main/kotlin/.../FeedConsumer.kt` | RabbitMQ consume |
| `features/feed/src/main/kotlin/.../di/feedModule.kt` | Koin module |
| `features/feed/src/test/kotlin/...` | Feature tests |
| `core/embed/` | Embedding client |
| `app/src/main/kotlin/.../Application.kt` | Wire `feedModule` + route mounting |

---

## Design Decisions (locked for MVP)

1. **MVP ships `GET /feed/for-you`**, not chronological-only with personalization later.
2. **Vectors re-rank inside SQL filters** — not unbounded nearest-neighbor over the full catalog.
3. **Traits are explainable filters**; embeddings handle fuzzy relevance.
4. **Keyset pagination only** — `(created_at, id)` for chrono, `(score, id)` for ranked.
5. **Fixed query budget** — batch `IN ?` for traits and media; no per-item DB calls.
6. **Embeddings on write** for text; async for images via RabbitMQ.
7. **No Redis** until Postgres profiling shows need.

---

## Open Questions (resolve during Phase feed-A)

| Question | Default for MVP |
|----------|-----------------|
| Embedding model & hosting | Document in PR; stub OK for initial CI |
| Trait prototype vectors | Average of 3–5 seed item embeddings per trait |
| `followers` visibility | Schema present; enforce in Phase feed-D+ when follow graph exists |
| Score weights (`w_rel`, etc.) | Constants in `FeedService.kt`; tune with logging |

---

## Related Documentation

- [user_module.md](./user_module.md) — `user_stats` cache pattern (do not lazy-recalc on feed reads)
- [traits_module.md](./traits_module.md) — trait schema ships in this migration
- [media_module.md](./media_module.md) — Phase feed-E async embeddings
- [conventions.md](./conventions.md) — pagination, tests, markdown
- [README.md](../README.md) — product context
