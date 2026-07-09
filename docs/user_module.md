# Guide: Building the User & Auth Module From Zero

Identity, OAuth sessions, trust cache, peer ratings, and user blocks.

**Status:** Doc complete · **Backend:** ✅ MVP · **Service:** `AuthService`, `UserService`

**Depends on:** — · **Unblocks:** [seller_profile](./seller_profile_module.md), [profile_portfolio](./profile_portfolio_module.md), [feed](./feed_module.md), [trade](./trade_module.md)

**Master plan:** [implementation_plan.md](./implementation_plan.md) (user work continues in Wave 3.5 for public ratings)

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#user--auth-module) · [auth_and_permissions.md](./auth_and_permissions.md) · [trust_events.md](./trust_events.md) · [conventions.md](./conventions.md)

---

## Step 1: Database Migration Setup

Define the tables to store user details, OAuth identities, sessions, rating details, and trust ledgers.

**Canonical DDL:** [schema.md](./schema.md#user--auth-module) in `apps/backend/db/migration/000001_init.up.sql`.

Key tables: `users`, `user_profiles` (includes `location_tag`, `seller_headline`), `user_identities`, `user_stats`, `user_sessions`, `user_ratings`, `user_trust_ledger`, `user_blocks`.

Example excerpt (see migration file for full definitions):

```sql
-- 1. Base Users Table (Narrow, index-friendly)
CREATE TABLE users (
    id bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    username VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. User Profiles (Stores mutable display & localization details)
CREATE TABLE user_profiles (
    user_id bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(100),
    avatar_url text,
    bio text,
    timezone VARCHAR(10),
    preferred_language VARCHAR(5),
    location_tag varchar(100),
    seller_headline varchar(160),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Simplified OAuth Identities (Linked by provider string, metadata in JSONB)
CREATE TABLE user_identities (
    id bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL CHECK (provider IN ('google', 'apple')),
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    provider_metadata jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_user_id)
);

-- 4. User Stats (Local cache for heavy aggregated ratings/trust calculations)
CREATE TABLE user_stats (
    user_id bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    explicit_rating_avg DECIMAL(3,2) DEFAULT 5.00,
    implicit_trust_score INT DEFAULT 100,
    last_calculated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. User Sessions (Enables token revocation and security controls)
CREATE TABLE user_sessions (
    id bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    device_info VARCHAR(100),
    ip_address VARCHAR(45),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_revoked boolean DEFAULT false NOT NULL
);

-- 6. Peer Ratings Log
CREATE TABLE user_ratings (
    id bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    reviewer_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reviewee_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating integer NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment text,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. Trust Ledger Log (Audit trail of score changes)
CREATE TABLE user_trust_ledger (
    id bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    delta integer NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    description text,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. User Moderation (Blocks / Mutes)
CREATE TABLE user_blocks (
    blocker_id bigint REFERENCES users(id) ON DELETE CASCADE,
    blocked_id bigint REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id)
);
```

---

## Step 2: Define SQLC Queries

Write the following database queries to `db/query/queries.sql` to specify how data is inserted, updated, and lazily aggregated.

### 1. Lazy Recalculation Averages
These queries perform time-decay calculations on ratings and sum trust score ledger entries on demand.
```sql
-- name: CalculateUserRatingAvg :one
SELECT 
    COALESCE(
        SUM(rating::float8 * EXP(-0.005 * (EXTRACT(EPOCH FROM (NOW() - created_at)) / 86400.0))) / 
        NULLIF(SUM(EXP(-0.005 * (EXTRACT(EPOCH FROM (NOW() - created_at)) / 86400.0))), 0),
        5.0
    )::float8 AS rating_avg
FROM user_ratings 
WHERE reviewee_id = $1 AND created_at >= NOW() - INTERVAL '365 days';

-- name: CalculateUserTrustScore :one
SELECT COALESCE(100 + SUM(delta), 100)::integer AS trust_score
FROM user_trust_ledger
WHERE user_id = $1;
```

### 2. Cache Mutations
```sql
-- name: GetUser :one
SELECT users.id, users.username, users.created_at,
       COALESCE(us.explicit_rating_avg, 5.00)::numeric(3,2) AS explicit_rating_avg,
       COALESCE(us.implicit_trust_score, 100)::integer AS implicit_trust_score,
       us.last_calculated_at
FROM users
LEFT JOIN user_stats us ON users.id = us.user_id
WHERE users.id = $1 LIMIT 1;

-- name: UpsertUserStats :one
INSERT INTO user_stats (user_id, explicit_rating_avg, implicit_trust_score, last_calculated_at)
VALUES ($1, $2, $3, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO UPDATE
SET explicit_rating_avg = EXCLUDED.explicit_rating_avg,
    implicit_trust_score = EXCLUDED.implicit_trust_score,
    last_calculated_at = CURRENT_TIMESTAMP
RETURNING *;

-- name: UpdateImplicitTrust :one
UPDATE user_stats
SET implicit_trust_score = implicit_trust_score + $2,
    last_calculated_at = CURRENT_TIMESTAMP
WHERE user_id = $1
RETURNING *;
```

### 3. Identity Provider and Audit Logs
```sql
-- name: FindUserByIdentity :one
SELECT users.id, users.username, users.created_at FROM users
JOIN user_identities ON users.id = user_identities.user_id
WHERE user_identities.provider = $1 AND user_identities.provider_user_id = $2 LIMIT 1;

-- name: LinkUserIdentity :one
INSERT INTO user_identities (user_id, provider, provider_user_id, email, provider_metadata)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: AddTrustLedgerEntry :one
INSERT INTO user_trust_ledger (user_id, delta, event_type, description)
VALUES ($1, $2, $3, $4)
RETURNING *;
```

Compile query methods by running SQLC generation in the backend directory:
```bash
sqlc generate
```

---

## Step 3: Go Application Integration

### 1. User Creation & Session Login (`auth.go`)
Upon user registration/login via OAuth:
1.  Check if the provider identity is linked.
2.  If it doesn't exist, create a new `User` record.
3.  Issue a short-lived JWT session token (`auth.SessionTokenLifetime`, **15 minutes**). Clients must re-call `AuthService.Authenticate` on a ~15-minute cadence (or before `exp`) to obtain a fresh token; expired tokens are rejected by the gRPC auth interceptor.
4.  **Crucial**: Immediately initialize the cache record inside `user_stats`:
    ```go
    var ratingDec pgtype.Numeric
    _ = ratingDec.Scan("5.00")
    var trustInt pgtype.Int4
    _ = trustInt.Scan(100)

    _, err = txQueries.UpsertUserStats(ctx, db.UpsertUserStatsParams{
        UserID:             user.ID,
        ExplicitRatingAvg:  ratingDec,
        ImplicitTrustScore: trustInt,
    })
    ```
5.  Link identity provider details by passing `provider` as a plain string parameter (e.g. `"google"` or `"apple"`).
6.  **Create `user_profiles` row on signup** via `CreateUserProfile` in the auth transaction.

### Auth policy

Full RPC matrix: [auth_and_permissions.md](./auth_and_permissions.md). Public profile reads are documented under seller/profile modules.

### 1b. Auth Provider Interface (`internal/auth/provider`)
OAuth integrations are abstracted behind `provider.AuthProvider`. Each provider (e.g. Google in `internal/auth/providers/google`) registers a factory via `provider.Register` and implements:
- `VerifyToken` — validate the upstream ID token and return a normalized `provider.Identity`
- `GetAccountInfo` / `CanVerifyToken` — used by `GetProviderAccountInfo`
- `Info` — metadata exposed through `GetAuthProviders`
- `HandleOAuthCallback` — HTTP OAuth redirect handler wired from `/api/auth/callback/{provider}`

`AuthService` holds a `map[string]provider.AuthProvider` built from environment OAuth configs. Add a new provider by implementing `AuthProvider`, calling `provider.Register("<id>", factory)` in that package's `init()`, and blank-importing the package from `auth.go`.

### 2. Lazy Recalculation Cache (`user.go`)
When querying `GetUserProfile`, evaluate if stats cache is stale (older than 1 hour):
```go
explicitRating := 5.00
implicitScore := int32(100)

isStale := !user.LastCalculatedAt.Valid || time.Since(user.LastCalculatedAt.Time) > 1*time.Hour
if isStale {
    // 1. Recalculate rating with 365-day time decay cutoff
    calculatedRating, err := s.queries.CalculateUserRatingAvg(ctx, userID)
    if err == nil {
        explicitRating = calculatedRating
    }

    // 2. Sum trust scores from ledger history
    calculatedTrust, err := s.queries.CalculateUserTrustScore(ctx, userID)
    if err == nil {
        implicitScore = calculatedTrust
    }

    // 3. Upsert scores to cache
    var ratingDec pgtype.Numeric
    _ = ratingDec.Scan(strconv.FormatFloat(explicitRating, 'f', 2, 64))
    var trustInt pgtype.Int4
    _ = trustInt.Scan(implicitScore)

    _, _ = s.queries.UpsertUserStats(ctx, db.UpsertUserStatsParams{
        UserID:             userID,
        ExplicitRatingAvg:  ratingDec,
        ImplicitTrustScore: trustInt,
    })
}
```

### 3. Trust scoring from other modules

When the [trade](./trade_module.md) and [validation](./validation_module.md) modules complete a handoff, they call **UserService internal helpers** (not public gRPC) to:

1. Insert `user_trust_ledger` with an `event_type` from [trust_events.md](./trust_events.md) (e.g. `TRADE_COMPLETED`).
2. Bump `user_stats.implicit_trust_score` in the same transaction.

`RecordPeerRating` is internal today; optional public `SubmitRating` RPC is Wave 3.5 in [implementation_plan.md](./implementation_plan.md).

Example pattern (implemented in validation/trade services, not a standalone `trade.go` in user package):

```go
_, err = txQueries.AddTrustLedgerEntry(ctx, db.AddTrustLedgerEntryParams{
    UserID:      userID,
    Delta:       15,
    EventType:   "TRADE_COMPLETED",
    Description: pgtype.Text{String: "Trade completed successfully", Valid: true},
})
_, err = txQueries.UpdateImplicitTrust(ctx, db.UpdateImplicitTrustParams{
    UserID:             userID,
    ImplicitTrustScore: 15,
})
```

---

## Implementation phases

### user-A — Core auth & identity ✅

- [x] Google OAuth + session JWT
- [x] Apple OAuth provider
- [x] `user_stats` seed on signup
- [x] Lazy trust/rating recalc (1h TTL)

### user-B — Blocks & admin ✅

- [x] `user_blocks` + `BlockUser` / `UnblockUser`
- [x] `UpdateImplicitTrust` (admin allowlist)

### user-C — Peer ratings (partial) 🔶

- [x] `user_ratings` table + SQLC
- [x] Internal `RecordPeerRating`
- [ ] Public `SubmitRating` RPC (Wave 3.5)

---

## Step 4: Verification

Reset the local Nix development PostgreSQL database schema and seed data to run tests:
```bash
# 1. Clean the postgres storage directory
rm -rf .devenv/state/postgres

# 2. Run background devenv initialization
devenv test

# 3. Execute backend service test suite
cd apps/backend && go test ./tests/...
```

---

## Related documentation

- [seller_profile_module.md](./seller_profile_module.md) — public profile RPCs on UserService
- [trust_events.md](./trust_events.md) — ledger event types
- [WALKTHROUGH.md](../WALKTHROUGH.md) — local setup
