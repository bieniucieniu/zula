# Guide: Building the Seller Profile Module From Zero

Public seller profiles and owner profile editing — **no separate seller account type**.

**Status:** Doc complete · **Backend:** 🔶 partial (seller-A, seller-B) · **Feature:** `features:user`

**Depends on:** [user_module.md](./user_module.md) · **Unblocks:** seller page UX (listings tab needs [feed](./feed_module.md))

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 1.4 (seller-C), Wave 2.4 (seller-D)

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#seller-profile-module) · [auth_and_permissions.md](./auth_and_permissions.md) · [profile_portfolio_module.md](./profile_portfolio_module.md) · [feed_module.md](./feed_module.md)

In Zula there is no separate “seller” account type: any user can post needs, offers, or trips. A **seller profile** is the public view of a `users` row plus `user_profiles`, trust stats, recent reviews, and active listings — what a buyer sees before starting a trade or chat.

Conventions: [conventions.md](./conventions.md) (thin clients, keyset pagination).

---

## Goals

| Goal | Detail |
|------|--------|
| **Public seller page** | `GET /api/sellers/{idOrUsername}` — display, trust, counts, optional recent reviews |
| **Owner profile** | `GET /api/users/me/profile` + `PATCH /api/users/me/profile` — edit bio, avatar, display name, locale |
| **Seller listings tab** | Reuse `GET /api/feed/by-author/{id}` (no duplicate query logic) |
| **Trust display** | Read `user_stats` cache; lazy recalc only on profile view (not on feed scroll) |
| **Safety** | Respect `user_blocks`; hide or 404 blocked profiles per policy |
| **No N+1** | Profile header in 1–2 queries; listings via existing feed batch pattern |

---

## Current state

| Piece | Status |
|-------|--------|
| `user_profiles` + `location_tag`, `seller_headline` | ✅ in `000001_init.sql` |
| `GET /api/sellers/{idOrUsername}` | ✅ Shipped |
| `GET /api/users/me/profile`, `PATCH /api/users/me/profile` | ✅ Shipped |
| `GET /api/users/{username}` (legacy trust-only) | ✅ Shipped |
| `GET /api/reviews/seller/{userId}` (keyset, `ProfileCursor`) | ✅ Shipped |
| `POST /api/users/{userId}/block` / `DELETE …/block` + policy A on profile view | ✅ Shipped |
| Profile row on signup | ✅ Shipped (`features:auth` signup flow) |
| `seller_activity_stats` table | ✅ Schema only — counts empty until feed |
| `GET /api/feed/by-author/{id}` on seller page | ⬜ Blocked on feed module (seller-C) |
| Avatar presigned upload | ⬜ seller-D / [media_module.md](./media_module.md) |
| `completed_trade_count` | ⬜ Blocked on trade module |

Legacy `GET /api/users/{id}/profile` remains public for backward compatibility; new clients use `GET /api/sellers/{idOrUsername}`. Auth: [auth_and_permissions.md](./auth_and_permissions.md).

---

## Architecture Overview

```text
Client
   │
   ├─ GET /api/sellers/{idOrUsername}       [public]
   │     └─ features:user → UserService
   │           ├─ Q1: user + profile + stats (single JOIN)
   │           ├─ optional Q2: seller_activity_counts (or JOIN subselect)
   │           ├─ optional Q3: recent ratings (LIMIT 5, separate query)
   │           └─ if viewer authenticated: block relationship flags
   │
   ├─ GET /api/feed/by-author/{id}          [public / auth]
   │     └─ features:feed → FeedService (see feed_module.md)
   │
   ├─ GET /api/users/me/profile             [auth]
   │     └─ UserService — same shape + private fields
   │
   └─ PATCH /api/users/me/profile           [auth]
         └─ UserService — validate + UPDATE user_profiles
```

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `features/user/.../UserRouting.kt` | HTTP paths, OpenAPI metadata, auth scopes |
| Service | `features/user/.../UserService.kt` | Business logic, transactions, block policy |
| DI | `features/user/.../UserModule.kt` | Koin module wiring |

**Page composition (client):** header from `GET /api/sellers/{idOrUsername}` + infinite scroll from `GET /api/feed/by-author/{id}`. Two REST calls, no combined mega-endpoint — keeps features focused and avoids N+1 across listings.

---

## Step 1: Database Changes

Schema for seller fields and activity cache is **already in** `core/database/src/main/resources/db/migration/000001_init.sql`. See [schema.md](./schema.md#seller-profile-module).

No separate `000003_seller_profile` migration — do not add one unless new columns are required later.

### 1.1 Indexes for profile lookups (optional, deferred)

```sql
-- username lookup (users.username already UNIQUE)
CREATE INDEX IF NOT EXISTS user_profiles_display_name_trgm_idx
    ON user_profiles USING gin (display_name gin_trgm_ops);
-- requires pg_trgm — defer unless search-by-name is MVP
```

---

## Step 2: REST / OpenAPI Surface

Extend DTOs in `core/openapi/src/main/kotlin/dto/` (or add `profile.kt` if the file grows too large). Regenerate spec with `./gradlew :core:openapi:build`. Full route index: [api_index.md](./api_index.md).

### 2.1 Routes (seller profile subset)

| Method | Path | Request | Response | Notes |
|--------|------|---------|----------|-------|
| `GET` | `/api/sellers/{idOrUsername}` | — | `SellerProfileResponse` | Lookup by UUIDv7 id or username |
| `GET` | `/api/users/me/profile` | — | `MyProfileResponse` | Owner-only private fields |
| `PATCH` | `/api/users/me/profile` | `UpdateMyProfileRequest` | `MyProfileResponse` | Partial update; all fields optional |
| `GET` | `/api/reviews/seller/{userId}` | `ProfileCursor`, `limit` query | `ListSellerReviewsResponse` | Keyset pagination |
| `POST` | `/api/users/{userId}/block` | — | `BlockUserResponse` | Viewer = blocker |
| `DELETE` | `/api/users/{userId}/block` | — | `UnblockUserResponse` | Viewer = blocker |
| `GET` | `/api/users/{id}/profile` | — | `UserResponse` | **Legacy** — deprecate |
| `PATCH` | `/api/admin/users/{id}/trust` | `UpdateTrustRequest` | `UserResponse` | Admin only |

### 2.2 DTO summaries

```kotlin
// core/openapi/src/main/kotlin/dto/profile.kt

@Serializable
data class SellerProfileResponse(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val sellerHeadline: String?,
    val locationTag: String?,
    val memberSince: String,              // RFC3339 from users.created_at
    val explicitRatingAvg: Double,
    val implicitTrustScore: Int,
    val ratingCount: Int,
    val activity: SellerActivityCounts?,
    val recentReviews: List<SellerReviewPreview>,
    val viewerHasBlocked: Boolean? = null,  // unset when anonymous
    val viewerIsBlocked: Boolean? = null,
)

@Serializable
data class SellerActivityCounts(
    val activeOffers: Int,
    val activeNeeds: Int,
    val activeTrips: Int,
    val fulfilledItems: Int,
)

@Serializable
data class SellerReviewPreview(
    val id: String,
    val reviewerUsername: String,
    val rating: Int,
    val comment: String?,
    val createdAt: String,
)

@Serializable
data class MyProfileResponse(
    val public: SellerProfileResponse,
    val timezone: String?,
    val preferredLanguage: String?,
    val email: String?,                   // from user_identities; owner only
)

@Serializable
data class UpdateMyProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val sellerHeadline: String? = null,
    val locationTag: String? = null,
    val timezone: String? = null,
    val preferredLanguage: String? = null,
)

@Serializable
data class ListSellerReviewsResponse(
    val reviews: List<SellerReviewPreview>,
    val nextCursor: ProfileCursor?,
    val hasMore: Boolean,
)
```

### Auth policy

| Route | Auth |
|-------|------|
| `GET /api/sellers/{idOrUsername}` | Public; optional JWT for block flags |
| `GET /api/feed/by-author/{id}` | Public (feed feature) |
| `GET /api/reviews/seller/{userId}` | Public |
| `GET /api/users/me/profile` | Required |
| `PATCH /api/users/me/profile` | Required; only own row |
| `POST` / `DELETE /api/users/{userId}/block` | Required |

Public routes are mounted **outside** the `authenticate("auth-jwt")` block in `UserRouting.kt`. Full matrix: [auth_and_permissions.md](./auth_and_permissions.md).

```kotlin
// features/user/src/main/kotlin/.../UserRouting.kt
fun Route.configureUserRouting(userService: UserService) {
    route("/api") {
        // Public — optional JWT enriches viewer-specific fields
        get("/sellers/{idOrUsername}") {
            val viewerId = call.optionalUserId()
            call.respond(userService.getSellerProfile(call.parameters["idOrUsername"]!!, viewerId))
        }
        get("/reviews/seller/{userId}") {
            call.respond(userService.listSellerReviews(/* … */))
        }

        authenticate("auth-jwt") {
            route("/users/me") {
                get("/profile") { call.respond(userService.getMyProfile(call.requireUserId())) }
                patch("/profile") {
                    val body = call.receive<UpdateMyProfileRequest>()
                    call.respond(userService.updateMyProfile(call.requireUserId(), body))
                }
            }
            post("/users/{userId}/block") { /* … */ }
            delete("/users/{userId}/block") { /* … */ }
        }
    }
}
```

Mount via Koin in `app/src/main/kotlin/Application.kt`:

```kotlin
routing {
    configureUserRouting(get())
    // other feature routes …
}
```

### Deprecation

Keep legacy `GET /api/users/{id}/profile` returning trust + username for backward compatibility; implement as a thin wrapper over `getSellerProfile` internals. New clients use `GET /api/sellers/{idOrUsername}`.

---

## Step 3: SQLDelight Queries

Add `core/database/src/main/sqldelight/profile.sq`. Regenerate with `./gradlew :core:database:generateSqlDelightInterface`.

### 3.1 Seller profile header (single query)

```sql
getSellerProfileById:
SELECT
    u.id,
    u.username,
    u.created_at AS member_since,
    up.display_name,
    up.avatar_url,
    up.bio,
    up.seller_headline,
    up.location_tag,
    COALESCE(us.explicit_rating_avg, 5.00) AS explicit_rating_avg,
    COALESCE(us.implicit_trust_score, 100) AS implicit_trust_score,
    us.last_calculated_at
FROM users u
LEFT JOIN user_profiles up ON up.user_id = u.id
LEFT JOIN user_stats us ON us.user_id = u.id
WHERE u.id = ?
LIMIT 1;

getSellerProfileByUsername:
SELECT
    u.id,
    u.username,
    u.created_at AS member_since,
    up.display_name,
    up.avatar_url,
    up.bio,
    up.seller_headline,
    up.location_tag,
    COALESCE(us.explicit_rating_avg, 5.00) AS explicit_rating_avg,
    COALESCE(us.implicit_trust_score, 100) AS implicit_trust_score,
    us.last_calculated_at
FROM users u
LEFT JOIN user_profiles up ON up.user_id = u.id
LEFT JOIN user_stats us ON us.user_id = u.id
WHERE u.username = ?
LIMIT 1;
```

### 3.2 Rating count + recent reviews

```sql
countUserRatings:
SELECT count(*) AS rating_count
FROM user_ratings
WHERE reviewee_id = ?;

listRecentSellerReviews:
SELECT
    ur.id,
    ur.rating,
    ur.comment,
    ur.created_at,
    reviewer.username AS reviewer_username
FROM user_ratings ur
JOIN users reviewer ON reviewer.id = ur.reviewer_id
WHERE ur.reviewee_id = ?
ORDER BY ur.created_at DESC, ur.id DESC
LIMIT ?;

-- Keyset on (created_at, id) for full reviews tab.
-- SQLDelight: use listSellerReviewsFirstPage when cursor is null; otherwise listSellerReviewsPage.
listSellerReviewsPage:
SELECT
    ur.id,
    ur.rating,
    ur.comment,
    ur.created_at,
    reviewer.username AS reviewer_username
FROM user_ratings ur
JOIN users reviewer ON reviewer.id = ur.reviewer_id
WHERE ur.reviewee_id = ?
  AND (ur.created_at, ur.id) < (?, ?)
ORDER BY ur.created_at DESC, ur.id DESC
LIMIT ?;
```

### 3.3 Activity counts

**Option A — denormalized table:**

```sql
getSellerActivityStats:
SELECT * FROM seller_activity_stats WHERE user_id = ?;

upsertSellerActivityStats:
INSERT INTO seller_activity_stats (...)
VALUES (...)
ON CONFLICT (user_id) DO UPDATE SET ...
RETURNING *;

recalculateSellerActivityStats:
SELECT
    count(*) FILTER (WHERE kind = 'offer' AND status = 'active') AS active_offer_count,
    count(*) FILTER (WHERE kind = 'need' AND status = 'active') AS active_need_count,
    count(*) FILTER (WHERE kind = 'trip' AND status = 'active') AS active_trip_count,
    count(*) FILTER (WHERE status = 'fulfilled') AS fulfilled_item_count
FROM feed_items
WHERE author_id = ?;
```

**Option B — MVP inline:** use `recalculateSellerActivityStats` on every profile read until `seller_activity_stats` lands.

### 3.4 Profile mutations (owner)

```sql
-- Nullable params: pass null from Kotlin to skip a field (COALESCE keeps existing value).
updateUserProfile:
UPDATE user_profiles
SET
    display_name = COALESCE(?, display_name),
    bio = COALESCE(?, bio),
    avatar_url = COALESCE(?, avatar_url),
    seller_headline = COALESCE(?, seller_headline),
    location_tag = COALESCE(?, location_tag),
    timezone = COALESCE(?, timezone),
    preferred_language = COALESCE(?, preferred_language),
    updated_at = CURRENT_TIMESTAMP
WHERE user_id = ?
RETURNING *;

ensureUserProfile:
INSERT INTO user_profiles (user_id, display_name)
VALUES (?, ?)
ON CONFLICT (user_id) DO NOTHING;
```

Create `user_profiles` row on first `PATCH /api/users/me/profile` if missing (registration today may not insert profile for all paths — align with `features:auth` signup flow).

### 3.5 Blocks

```sql
isBlockedEitherDirection:
SELECT EXISTS (
    SELECT 1 FROM user_blocks
    WHERE (blocker_id = ? AND blocked_id = ?)
       OR (blocker_id = ? AND blocked_id = ?)
) AS blocked;

hasViewerBlockedSeller:
SELECT EXISTS (
    SELECT 1 FROM user_blocks
    WHERE blocker_id = ? AND blocked_id = ?
) AS blocked;

blockUser:
INSERT INTO user_blocks (blocker_id, blocked_id)
VALUES (?, ?)
ON CONFLICT DO NOTHING;

unblockUser:
DELETE FROM user_blocks
WHERE blocker_id = ? AND blocked_id = ?;
```

**Blocked profile policy (choose one, document in service):**

| Policy | Behavior |
|--------|----------|
| **A (recommended)** | Return `404 Not Found` if viewer blocked seller OR seller blocked viewer |
| **B** | Return profile but `GET /api/feed/by-author/{id}` empty + `viewerHasBlocked` flags |

Use **A** for simpler safety; flags in DTO still useful when policy B is needed later.

---

## Step 4: Kotlin Service (`features/user/.../UserService.kt`)

Register in Koin via `UserModule.kt`:

```kotlin
val userModule = module {
    single { UserService(get()) }  // DatabaseQueries from core:database
}
```

### 4.1 `getSellerProfile` flow

```kotlin
suspend fun getSellerProfile(idOrUsername: String, viewerId: Long?): SellerProfileResponse {
    val row = resolveSellerRow(idOrUsername)  // by id or username

    if (viewerId != null) {
        if (queries.isBlockedEitherDirection(viewerId, row.id, row.id, viewerId).executeAsOne()) {
            throw NotFoundException("user not found")
        }
    }

    val (explicit, implicit) = resolveTrustScores(row.id, row)  // lazy recalc if stale — OK here, not on feed

    val ratingCount = queries.countUserRatings(row.id).executeAsOne()
    val activity = resolveActivityCounts(row.id)
    val previews = queries.listRecentSellerReviews(row.id, 5).executeAsList()

    return mapToSellerProfileResponse(row, explicit, implicit, ratingCount, activity, previews)
        .let { resp ->
            if (viewerId != null) {
                resp.copy(
                    viewerHasBlocked = queries.hasViewerBlockedSeller(viewerId, row.id).executeAsOne(),
                    viewerIsBlocked = /* symmetric check */,
                )
            } else resp
        }
}
```

**Query budget (MVP):**

| Step | Queries |
|------|---------|
| Resolve user + profile + stats | 1 |
| Trust lazy recalc (if stale) | 0–2 |
| Rating count | 1 |
| Activity counts | 0–1 (cached) or 1 recalc |
| Recent reviews | 1 |
| Block flags | 0–1 |
| **Typical total** | **4–6** (acceptable for single profile page; not per feed item) |

### 4.2 `updateMyProfile` flow

```kotlin
suspend fun updateMyProfile(ownerId: Long, req: UpdateMyProfileRequest): MyProfileResponse {
    validateProfileFields(req)

    queries.ensureUserProfile(ownerId, defaultDisplayName(ownerId))
    queries.updateUserProfile(
        displayName = req.displayName,
        bio = req.bio,
        avatarUrl = req.avatarUrl,
        sellerHeadline = req.sellerHeadline,
        locationTag = req.locationTag,
        timezone = req.timezone,
        preferredLanguage = req.preferredLanguage,
        userId = ownerId,
    )
    // avatarUrl must be a key/url already uploaded via presigned MinIO flow (Phase D)
    return getMyProfile(ownerId)
}
```

**Validation (backend):**

| Field | Rule |
|-------|------|
| `displayName` | 1–100 chars, trim whitespace |
| `bio` | max 2000 chars |
| `sellerHeadline` | max 160 chars |
| `locationTag` | max 100 chars |
| `avatarUrl` | allowed bucket/host prefix only |
| `timezone` | IANA or fixed allowlist |
| `preferredLanguage` | ISO 639-1 (`en`, `pl`, …) |

### 4.3 Trust score refresh

Reuse existing lazy logic from legacy profile read (1-hour staleness). **Only** run on:

- `getSellerProfile` / `getMyProfile` / legacy profile endpoint

Never on feed list joins (see [feed_module.md](./feed_module.md) N+1 checklist).

Extract shared helper:

```kotlin
private suspend fun resolveTrustScores(
    userId: Long,
    cached: GetSellerProfileById,
): Pair<Double, Int>
```

### 4.4 Feed integration

Seller listings tab = client calls:

1. `GET /api/sellers/{idOrUsername}` — header
2. `GET /api/feed/by-author/{id}?kind=offer` — keyset grid (server-side filter preferred)

Extend feed query param when feed ships (Phase C):

```kotlin
// GET /api/feed/by-author/{id}?kind=offer|need|trip
```

Server-side filter preferred (thin client rule).

### 4.5 Keep `seller_activity_stats` in sync

In `FeedService.createFeedItem` / status update handlers (feed module), inject `SellerActivityWriter`:

```kotlin
// Koin contract — features:feed calls features:user without direct import
interface SellerActivityWriter {
    suspend fun syncSellerActivityStats(authorId: Long)
}
```

Or inline SQL increment in the same feed transaction — avoids drift.

---

## Step 5: Avatar Upload (MinIO)

Profile editing includes `avatarUrl`. Clients should not send raw image bytes over REST.

**Flow:**

```text
1. GET /api/users/me/profile → need new avatar
2. POST /api/media/avatar-upload → presigned PUT URL + object_key  (Phase D; features:media)
3. Client PUT to MinIO
4. PATCH /api/users/me/profile { avatarUrl: public_or_signed_url_for_key }
```

Add avatar upload route when implementing Phase D; until then accept HTTPS URLs from trusted CDNs in dev only.

---

## Step 6: N+1 and Performance Checklist

| Risk | Mitigation |
|------|------------|
| Loading listings inside `getSellerProfile` | **Do not** — separate `GET /api/feed/by-author/{id}` |
| Per-review reviewer profile fetch | JOIN `reviewer` in `listSellerReviewsPage` |
| Trust recalc on every feed card | JOIN `user_stats` only on feed queries |
| `COUNT(*)` on `feed_items` every view | `seller_activity_stats` cache |
| Legacy profile in a loop over authors | Batch or JOIN (feed module rule) |

---

## Step 7: Caching Summary

| Data | Strategy |
|------|----------|
| Trust / rating avg | `user_stats` + 1h lazy refresh on profile read |
| Activity counts | `seller_activity_stats` + write-through on feed changes |
| Profile fields | Source of truth `user_profiles`; no extra cache |
| Recent reviews | Query live; small `LIMIT 5` |
| Full reviews tab | Keyset paginated, no cache |

---

## Step 8: Implementation Phases

### Phase seller-A — Expose profile data ✅

- [x] `GET /api/sellers/{idOrUsername}` by id and username
- [x] Return `user_profiles` fields + trust from `user_stats`
- [x] `GET /api/users/me/profile`, `PATCH /api/users/me/profile`
- [x] Profile row on user registration (`features:auth`)
- [x] Tests: read by username, update own profile

### Phase seller-B — Seller signals ✅

- [x] `countUserRatings` + recent reviews on profile
- [x] `GET /api/reviews/seller/{userId}` with keyset pagination (`ProfileCursor`)
- [x] `seller_headline`, `location_tag` columns
- [x] Block / unblock routes + profile 404 when blocked (policy A)

### Phase seller-C — Listings integration (depends on feed-C)

- [ ] `GET /api/feed/by-author/{id}` on seller page
- [ ] `kind` query param on author feed
- [ ] Sync `seller_activity_stats` from feed writes via `SellerActivityWriter`
- [ ] Tests: counts update when item created / fulfilled

### Phase seller-D — Media & polish

- [ ] `POST /api/media/avatar-upload` presigned URL ([media_module.md](./media_module.md))
- [ ] Display name search (pg_trgm) if needed
- [ ] `completed_trade_count` when trade module exists

---

## Step 9: Verification

```bash
./gradlew :core:database:generateSqlDelightInterface
./gradlew :core:openapi:build
./gradlew :features:user:test
./gradlew test

# reset DB if migrations added
./gradlew test
db-seed
```

### Manual checks

- `GET /api/sellers/alice` → display_name, bio, trust
- `GET /api/users/me/profile` (auth) → includes timezone
- `PATCH /api/users/me/profile` → persists
- `GET /api/feed/by-author/{alice}` → only alice items, respects blocks
- `POST /api/users/{id}/block` → seller profile returns 404

### Tests to add

| Case | Expected |
|------|----------|
| Unknown username | `404 Not Found` |
| Stale `user_stats` | Recalc + upsert after `getSellerProfile` |
| Viewer blocked seller | `404 Not Found` (policy A) |
| Update another user's profile | `403 Forbidden` |
| Reviews pagination | Stable keyset, no duplicates |

---

## Step 10: File Checklist

| Path | Purpose |
|------|---------|
| `core/database/src/main/resources/db/migration/000001_init.sql` | Profiles + `seller_activity_stats` |
| `core/database/src/main/sqldelight/profile.sq` | SQLDelight profile/seller queries |
| `core/openapi/src/main/kotlin/dto/profile.kt` | Seller profile DTOs |
| `core/openapi/src/main/resources/openapi.yaml` | Generated / merged spec |
| `features/user/src/main/kotlin/.../UserRouting.kt` | HTTP routes + auth scopes |
| `features/user/src/main/kotlin/.../UserService.kt` | Profile handlers + block policy |
| `features/user/src/main/kotlin/.../UserModule.kt` | Koin wiring |
| `features/user/src/test/kotlin/.../UserProfileTest.kt` | Profile tests |
| `features/auth/src/main/kotlin/.../AuthService.kt` | Profile row on signup |

---

## Design Decisions (locked for MVP)

1. **Seller = user** — no separate seller entity or table.
2. **Two REST calls for seller page** — seller profile + author feed; no combined endpoint.
3. **Trust lazy recalc on profile view only** — same 1h TTL as today.
4. **Public discovery** — seller profile unauthenticated; blocks enforced when viewer is known.
5. **Activity counts denormalized** once feed module ships — avoid per-view `COUNT` at scale.
6. **Backend validates all profile fields** — clients are thin.

---

## Open Questions

| Question | MVP default |
|----------|-------------|
| Show email on `GET /api/users/me/profile`? | Yes, from `user_identities`; never on public profile |
| Hide zero-offer profiles? | No — show empty state in client |
| Username change | Out of scope — username immutable after signup |
| Report user | [moderation_module.md](./moderation_module.md) Wave 5 |

---

## Related Documentation

- [user_module.md](./user_module.md) — trust ledger, `user_stats` cache, auth signup
- [feed_module.md](./feed_module.md) — `feed_items`, author feed, author JOIN pattern
- [AGENTS.md](../AGENTS.md) — keyset pagination, thin-client rules
