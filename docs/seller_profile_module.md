# Guide: Building the Seller Profile Module From Zero

Public seller profiles and owner profile editing — **no separate seller account type**.

**Status:** Doc complete · **Backend:** 🔶 partial (seller-A, seller-B) · **Service:** `UserService`

**Depends on:** [user_module.md](./user_module.md) · **Unblocks:** seller page UX (listings tab needs [feed](./feed_module.md))

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 1.4 (seller-C), Wave 2.4 (seller-D)

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#seller-profile-module) · [auth_and_permissions.md](./auth_and_permissions.md) · [profile_portfolio_module.md](./profile_portfolio_module.md) · [feed_module.md](./feed_module.md)

In Zula there is no separate “seller” account type: any user can post needs, offers, or trips. A **seller profile** is the public view of a `users` row plus `user_profiles`, trust stats, recent reviews, and active listings — what a buyer sees before starting a trade or chat.

Conventions: [conventions.md](./conventions.md) (thin clients, keyset pagination).

---

## Goals

| Goal | Detail |
|------|--------|
| **Public seller page** | `GetSellerProfile` — display, trust, counts, optional recent reviews |
| **Owner profile** | `GetMyProfile` + `UpdateMyProfile` — edit bio, avatar, display name, locale |
| **Seller listings tab** | Reuse `FeedService.ListFeedByAuthor` (no duplicate query logic) |
| **Trust display** | Read `user_stats` cache; lazy recalc only on profile view (not on feed scroll) |
| **Safety** | Respect `user_blocks`; hide or 404 blocked profiles per policy |
| **No N+1** | Profile header in 1–2 queries; listings via existing feed batch pattern |

---

## Current state

| Piece | Status |
|-------|--------|
| `user_profiles` + `location_tag`, `seller_headline` | ✅ in `000001_init.up.sql` |
| `GetSellerProfile` (by id / username) | ✅ Shipped |
| `GetMyProfile`, `UpdateMyProfile` | ✅ Shipped |
| `GetUserByUsername` | ✅ Shipped (legacy `UserResponse`) |
| `ListSellerReviews` (keyset, `ProfileCursor`) | ✅ Shipped |
| `BlockUser` / `UnblockUser` + policy A on profile view | ✅ Shipped |
| Profile row on signup | ✅ Shipped (`auth.go`) |
| `seller_activity_stats` table | ✅ Schema only — counts empty until feed |
| `ListFeedByAuthor` on seller page | ⬜ Blocked on feed module (seller-C) |
| Avatar presigned upload | ⬜ seller-D / [media_module.md](./media_module.md) |
| `completed_trade_count` | ⬜ Blocked on trade module |

`GetUserProfile` remains public for backward compatibility; new clients use `GetSellerProfile`. Auth: [auth_and_permissions.md](./auth_and_permissions.md).

---

## Architecture Overview

```text
Client
   │
   ├─ GetSellerProfile(user_id | username)     [public]
   │     └─ UserService
   │           ├─ Q1: user + profile + stats (single JOIN)
   │           ├─ optional Q2: seller_activity_counts (or JOIN subselect)
   │           ├─ optional Q3: recent ratings (LIMIT 5, separate query)
   │           └─ if viewer authenticated: block relationship flags
   │
   ├─ ListFeedByAuthor(author_id, cursor)      [public / auth]
   │     └─ FeedService (see feed_module.md)
   │
   ├─ GetMyProfile()                           [auth]
   │     └─ UserService — same shape + private fields
   │
   └─ UpdateMyProfile(...)                     [auth]
         └─ UserService — validate + UPDATE user_profiles
```

**Page composition (client):** header from `GetSellerProfile` + infinite scroll from `ListFeedByAuthor`. Two RPCs, no combined mega-endpoint — keeps services focused and avoids N+1 across listings.

---

## Step 1: Database Changes

Schema for seller fields and activity cache is **already in** `apps/backend/db/migration/000001_init.up.sql`. See [schema.md](./schema.md#seller-profile-module).

No separate `000003_seller_profile` migration — do not add one unless new columns are required later.

### 1.1 Indexes for profile lookups (optional, deferred)

```sql
-- username lookup (users.username already UNIQUE)
CREATE INDEX IF NOT EXISTS user_profiles_display_name_trgm_idx
    ON user_profiles USING gin (display_name gin_trgm_ops);
-- requires pg_trgm — defer unless search-by-name is MVP
```

---

## Step 2: Protocol Buffers

Extend `apps/backend/internal/apitypes/` (or add `profile` if file grows too large).

```protobuf
syntax = "proto3";
package zula;

service UserService {
    // existing
    rpc GetUserProfile(GetUserRequest) returns (UserResponse);  // deprecate → SellerProfileResponse

    // seller / profile (MVP)
    rpc GetSellerProfile(GetSellerProfileRequest) returns (SellerProfileResponse);
    rpc GetMyProfile(GetMyProfileRequest) returns (MyProfileResponse);
    rpc UpdateMyProfile(UpdateMyProfileRequest) returns (MyProfileResponse);

    // reviews tab
    rpc ListSellerReviews(ListSellerReviewsRequest) returns (ListSellerReviewsResponse);

    // moderation (viewer)
    rpc BlockUser(BlockUserRequest) returns (BlockUserResponse);
    rpc UnblockUser(UnblockUserRequest) returns (UnblockUserResponse);

    rpc UpdateImplicitTrust(UpdateTrustRequest) returns (UserResponse);  // admin, existing
}

message GetSellerProfileRequest {
    oneof lookup {
        string user_id = 1;
        string username = 2;
    }
}

message SellerProfileResponse {
    string user_id = 1;
    string username = 2;
    string display_name = 3;
    string avatar_url = 4;
    string bio = 5;
    string seller_headline = 6;
    string location_tag = 7;
    string member_since = 8;           // RFC3339 from users.created_at

    double explicit_rating_avg = 9;
    int32 implicit_trust_score = 10;
    int32 rating_count = 11;

    SellerActivityCounts activity = 12;
    repeated SellerReviewPreview recent_reviews = 13;

    // viewer-specific; unset when anonymous
    bool viewer_has_blocked = 14;
    bool viewer_is_blocked = 15;
}

message SellerActivityCounts {
    int32 active_offers = 1;
    int32 active_needs = 2;
    int32 active_trips = 3;
    int32 fulfilled_items = 4;
}

message SellerReviewPreview {
    string id = 1;
    string reviewer_username = 2;
    int32 rating = 3;
    string comment = 4;
    string created_at = 5;
}

message GetMyProfileRequest {}

message MyProfileResponse {
    SellerProfileResponse public = 1;
    string timezone = 2;
    string preferred_language = 3;
    string email = 4;  // from user_identities; owner only, optional
}

message UpdateMyProfileRequest {
    optional string display_name = 1;
    optional string bio = 2;
    optional string avatar_url = 3;
    optional string seller_headline = 4;
    optional string location_tag = 5;
    optional string timezone = 6;
    optional string preferred_language = 7;
}

message ListSellerReviewsRequest {
    string user_id = 1;
    ProfileCursor cursor = 2;   // document — not FeedCursor
    int32 limit = 3;
}

message ListSellerReviewsResponse {
    repeated SellerReviewPreview reviews = 1;
    ProfileCursor next_cursor = 2;
    bool has_more = 3;
}

message BlockUserRequest { string user_id = 1; }
message BlockUserResponse { bool ok = 1; }
message UnblockUserRequest { string user_id = 1; }
message UnblockUserResponse { bool ok = 1; }
```

### Auth policy

| RPC | Auth |
|-----|------|
| `GetSellerProfile` | Public; optional token for block flags |
| `ListFeedByAuthor` | Public (feed module) |
| `ListSellerReviews` | Public |
| `GetMyProfile` | Required |
| `UpdateMyProfile` | Required; only own row |
| `BlockUser` / `UnblockUser` | Required |

Add to `unprotectedMethods` in `auth.go`:

```go
"/zula.UserService/GetSellerProfile":   true,
"/zula.UserService/ListSellerReviews":  true,
```

Register updated `UserService` on `app.App` (already wired).

### Deprecation

Keep `GetUserProfile` temporarily returning trust + username for backward compatibility; implement as a thin wrapper over `GetSellerProfile` internals or mark deprecated in proto comments. New clients use `GetSellerProfile`.

---

## Step 3: SQLC Queries

Add `apps/backend/db/query/profile.sql`.

### 3.1 Seller profile header (single query)

```sql
-- name: GetSellerProfileByID :one
SELECT
    u.id,
    u.username,
    u.created_at AS member_since,
    up.display_name,
    up.avatar_url,
    up.bio,
    up.seller_headline,
    up.location_tag,
    COALESCE(us.explicit_rating_avg, 5.00)::numeric(3,2) AS explicit_rating_avg,
    COALESCE(us.implicit_trust_score, 100)::integer AS implicit_trust_score,
    us.last_calculated_at
FROM users u
LEFT JOIN user_profiles up ON up.user_id = u.id
LEFT JOIN user_stats us ON us.user_id = u.id
WHERE u.id = $1
LIMIT 1;

-- name: GetSellerProfileByUsername :one
-- same SELECT, WHERE u.username = $1
```

### 3.2 Rating count + recent reviews

```sql
-- name: CountUserRatings :one
SELECT count(*)::int AS rating_count
FROM user_ratings
WHERE reviewee_id = $1;

-- name: ListRecentSellerReviews :many
SELECT
    ur.id,
    ur.rating,
    ur.comment,
    ur.created_at,
    reviewer.username AS reviewer_username
FROM user_ratings ur
JOIN users reviewer ON reviewer.id = ur.reviewer_id
WHERE ur.reviewee_id = $1
ORDER BY ur.created_at DESC, ur.id DESC
LIMIT $2;

-- name: ListSellerReviews :many
-- keyset on (created_at, id) for full reviews tab
SELECT ...
WHERE ur.reviewee_id = $1
  AND (
      sqlc.narg(cursor_created_at)::timestamptz IS NULL
      OR (ur.created_at, ur.id) < (sqlc.narg(cursor_created_at)::timestamptz, sqlc.narg(cursor_id)::bigint)
  )
ORDER BY ur.created_at DESC, ur.id DESC
LIMIT sqlc.arg(page_limit);
```

### 3.3 Activity counts

**Option A — denormalized table:**

```sql
-- name: GetSellerActivityStats :one
SELECT * FROM seller_activity_stats WHERE user_id = $1;

-- name: UpsertSellerActivityStats :one
INSERT INTO seller_activity_stats (...)
ON CONFLICT (user_id) DO UPDATE ...
RETURNING *;

-- name: RecalculateSellerActivityStats :one
SELECT
    count(*) FILTER (WHERE kind = 'offer' AND status = 'active')::int AS active_offer_count,
    count(*) FILTER (WHERE kind = 'need' AND status = 'active')::int AS active_need_count,
    count(*) FILTER (WHERE kind = 'trip' AND status = 'active')::int AS active_trip_count,
    count(*) FILTER (WHERE status = 'fulfilled')::int AS fulfilled_item_count
FROM feed_items
WHERE author_id = $1;
```

**Option B — MVP inline:** use `RecalculateSellerActivityStats` on every profile read until `seller_activity_stats` lands.

### 3.4 Profile mutations (owner)

```sql
-- name: UpdateUserProfile :one
UPDATE user_profiles
SET
    display_name = COALESCE(sqlc.narg(display_name), display_name),
    bio = COALESCE(sqlc.narg(bio), bio),
    avatar_url = COALESCE(sqlc.narg(avatar_url), avatar_url),
    seller_headline = COALESCE(sqlc.narg(seller_headline), seller_headline),
    location_tag = COALESCE(sqlc.narg(location_tag), location_tag),
    timezone = COALESCE(sqlc.narg(timezone), timezone),
    preferred_language = COALESCE(sqlc.narg(preferred_language), preferred_language),
    updated_at = CURRENT_TIMESTAMP
WHERE user_id = $1
RETURNING *;

-- name: EnsureUserProfile :exec
INSERT INTO user_profiles (user_id, display_name)
VALUES ($1, $2)
ON CONFLICT (user_id) DO NOTHING;
```

Create `user_profiles` row on first `UpdateMyProfile` if missing (registration today may not insert profile for all paths — align with `auth.go` signup flow).

### 3.5 Blocks

```sql
-- name: IsBlockedEitherDirection :one
SELECT EXISTS (
    SELECT 1 FROM user_blocks
    WHERE (blocker_id = $1 AND blocked_id = $2)
       OR (blocker_id = $2 AND blocked_id = $1)
) AS blocked;

-- name: HasViewerBlockedSeller :one
SELECT EXISTS (
    SELECT 1 FROM user_blocks
    WHERE blocker_id = $1 AND blocked_id = $2
) AS blocked;

-- name: BlockUser :exec
INSERT INTO user_blocks (blocker_id, blocked_id)
VALUES ($1, $2)
ON CONFLICT DO NOTHING;

-- name: UnblockUser :exec
DELETE FROM user_blocks
WHERE blocker_id = $1 AND blocked_id = $2;
```

**Blocked profile policy (choose one, document in service):**

| Policy | Behavior |
|--------|----------|
| **A (recommended)** | Return `NotFound` if viewer blocked seller OR seller blocked viewer |
| **B** | Return profile but `ListFeedByAuthor` empty + `viewer_has_blocked` flags |

Use **A** for simpler safety; flags in proto still useful when policy B is needed later.

---

## Step 4: Go Service (`internal/service/user.go`)

### 4.1 `GetSellerProfile` flow

```go
func (s *UserService) GetSellerProfile(ctx context.Context, req *pb.GetSellerProfileRequest) (*pb.SellerProfileResponse, error) {
    row, err := s.resolveSellerRow(ctx, req) // by id or username
    if err != nil { return nil, err }

    viewerID, hasViewer := viewerFromContext(ctx)
    if hasViewer {
        if blocked, _ := s.queries.IsBlockedEitherDirection(ctx, viewerID, row.ID); blocked {
            return nil, status.Error(codes.NotFound, "user not found")
        }
    }

    explicit, implicit := s.resolveTrustScores(ctx, row) // lazy recalc if stale — OK here, not on feed

    ratingCount, _ := s.queries.CountUserRatings(ctx, row.ID)
    activity := s.resolveActivityCounts(ctx, row.ID)
    previews, _ := s.queries.ListRecentSellerReviews(ctx, row.ID, 5)

    resp := mapToSellerProfileResponse(row, explicit, implicit, ratingCount, activity, previews)
    if hasViewer {
        resp.ViewerHasBlocked, _ = ...
        resp.ViewerIsBlocked, _ = ...
    }
    return resp, nil
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

### 4.2 `UpdateMyProfile` flow

```go
func (s *UserService) UpdateMyProfile(ctx context.Context, req *pb.UpdateMyProfileRequest) (*pb.MyProfileResponse, error) {
    ownerID := requireViewer(ctx)
    if err := validateProfileFields(req); err != nil { return nil, err }

    _ = s.queries.EnsureUserProfile(ctx, ownerID, defaultDisplayName(ctx))
    updated, err := s.queries.UpdateUserProfile(ctx, ...)
    // avatar_url must be a key/url already uploaded via presigned MinIO flow (Phase D)
    return s.GetMyProfile(ctx, &pb.GetMyProfileRequest{})
}
```

**Validation (backend):**

| Field | Rule |
|-------|------|
| `display_name` | 1–100 chars, trim whitespace |
| `bio` | max 2000 chars |
| `seller_headline` | max 160 chars |
| `location_tag` | max 100 chars |
| `avatar_url` | allowed bucket/host prefix only |
| `timezone` | IANA or fixed allowlist |
| `preferred_language` | ISO 639-1 (`en`, `pl`, …) |

### 4.3 Trust score refresh

Reuse existing lazy logic from `GetUserProfile` (1-hour staleness). **Only** run on:

- `GetSellerProfile` / `GetMyProfile` / `GetUserProfile`

Never on feed list joins (see [feed_module.md](./feed_module.md) N+1 checklist).

Extract shared helper:

```go
func (s *UserService) resolveTrustScores(ctx context.Context, userID int64, cached GetSellerProfileRow) (float64, int32)
```

### 4.4 Feed integration

Seller listings tab = client calls:

1. `GetSellerProfile` — header
2. `FeedService.ListFeedByAuthor` — keyset grid filtered to `kind = offer` on client or via extended request

Extend `ListFeedByAuthorRequest` in feed proto (Phase C):

```protobuf
optional string kind_filter = 4;  // "offer" | "need" | "trip" | empty = all
```

Server-side filter preferred (thin client rule).

### 4.5 Keep `seller_activity_stats` in sync

In `FeedService.CreateFeedItem` / status update handlers (feed module):

```go
// after commit
s.userService.BumpSellerActivityStats(ctx, authorID)
```

Or inline SQL increment in the same feed transaction — avoids drift.

---

## Step 5: Avatar Upload (MinIO)

Profile editing includes `avatar_url`. Clients should not send raw image bytes over gRPC.

**Flow:**

```text
1. GetMyProfile → need new avatar
2. UserService.RequestAvatarUpload → presigned PUT URL + object_key  (Phase D RPC)
3. Client PUT to MinIO
4. UpdateMyProfile(avatar_url = public_or_signed_url_for_key)
```

Add `RequestAvatarUpload` when implementing Phase D; until then accept HTTPS URLs from trusted CDNs in dev only.

---

## Step 6: N+1 and Performance Checklist

| Risk | Mitigation |
|------|------------|
| Loading listings inside `GetSellerProfile` | **Do not** — separate `ListFeedByAuthor` RPC |
| Per-review reviewer profile fetch | JOIN `reviewer` in `ListSellerReviews` |
| Trust recalc on every feed card | JOIN `user_stats` only on feed queries |
| `COUNT(*)` on `feed_items` every view | `seller_activity_stats` cache |
| `GetUserProfile` in a loop over authors | Batch or JOIN (feed module rule) |

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

- [x] `GetSellerProfile` by `user_id` and `username`
- [x] Return `user_profiles` fields + trust from `user_stats`
- [x] `GetMyProfile`, `UpdateMyProfile`
- [x] Profile row on user registration (`auth.go`)
- [x] Tests: read by username, update own profile

### Phase seller-B — Seller signals ✅

- [x] `CountUserRatings` + recent reviews on profile
- [x] `ListSellerReviews` with keyset pagination (`ProfileCursor`)
- [x] `seller_headline`, `location_tag` columns
- [x] Block / unblock RPCs + profile 404 when blocked (policy A)

### Phase seller-C — Listings integration (depends on feed-C)

- [ ] `ListFeedByAuthor` on seller page
- [ ] `kind_filter` on author feed
- [ ] Sync `seller_activity_stats` from feed writes
- [ ] Tests: counts update when item created / fulfilled

### Phase seller-D — Media & polish

- [ ] `RequestAvatarUpload` presigned URL RPC ([media_module.md](./media_module.md))
- [ ] Display name search (pg_trgm) if needed
- [ ] `completed_trade_count` when trade module exists

---

## Step 9: Verification

```bash
# devenv shell
gen-openapi
cd apps/backend && sqlc generate

# reset DB if migrations added
devenv test

db-seed
cd apps/backend && go test ./...

# Manual checks
# - GetSellerProfile(username="alice") → display_name, bio, trust
# - GetMyProfile (auth) → includes timezone
# - UpdateMyProfile → persists
# - ListFeedByAuthor(alice) → only alice items, respects blocks
# - BlockUser → GetSellerProfile returns NotFound
```

### Tests to add

| Case | Expected |
|------|----------|
| Unknown username | `NotFound` |
| Stale `user_stats` | Recalc + upsert after `GetSellerProfile` |
| Viewer blocked seller | `NotFound` (policy A) |
| Update another user's profile | `PermissionDenied` |
| Reviews pagination | Stable keyset, no duplicates |

---

## Step 10: File Checklist

| Path | Purpose |
|------|---------|
| `apps/backend/db/migration/000001_init.up.sql` | Profiles + `seller_activity_stats` |
| `apps/backend/db/query/profile.sql` | SQLC profile/seller queries |
| `apps/backend/internal/apitypes/` | Extended messages + RPCs |
| `apps/backend/internal/service/user_profile.go` | Profile handlers |
| `apps/backend/tests/service/user_profile_test.go` | Tests |
| `apps/backend/internal/service/auth.go` | `unprotectedMethods`, profile on signup |

---

## Design Decisions (locked for MVP)

1. **Seller = user** — no separate seller entity or table.
2. **Two RPCs for seller page** — `GetSellerProfile` + `ListFeedByAuthor`; no combined endpoint.
3. **Trust lazy recalc on profile view only** — same 1h TTL as today.
4. **Public discovery** — `GetSellerProfile` unauthenticated; blocks enforced when viewer is known.
5. **Activity counts denormalized** once feed module ships — avoid per-view `COUNT` at scale.
6. **Backend validates all profile fields** — clients are thin.

---

## Open Questions

| Question | MVP default |
|----------|-------------|
| Show email on `GetMyProfile`? | Yes, from `user_identities`; never on public profile |
| Hide zero-offer profiles? | No — show empty state in client |
| Username change | Out of scope — username immutable after signup |
| Report user | [moderation_module.md](./moderation_module.md) Wave 5 |

---

## Related Documentation

- [user_module.md](./user_module.md) — trust ledger, `user_stats` cache, auth signup
- [feed_module.md](./feed_module.md) — `feed_items`, `ListFeedByAuthor`, author JOIN pattern
- [AGENTS.md](../AGENTS.md) — keyset pagination, thin-client rules
