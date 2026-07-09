# Guide: Building the User & Auth Module From Zero

Identity, OAuth sessions, trust cache, peer ratings, and user blocks.

**Status:** Doc complete · **Backend:** ✅ MVP · **Feature:** `features:auth`, `features:user`

**Depends on:** — · **Unblocks:** [seller_profile](./seller_profile_module.md), [profile_portfolio](./profile_portfolio_module.md), [feed](./feed_module.md), [trade](./trade_module.md)

**Master plan:** [implementation_plan.md](./implementation_plan.md) (user work continues in Wave 3.5 for public ratings)

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#user--auth-module) · [auth_and_permissions.md](./auth_and_permissions.md) · [trust_events.md](./trust_events.md) · [conventions.md](./conventions.md)

---

## Architecture

Ktor modular layout per [architecture.md](./architecture.md#ktor-project-layout). User & auth span two feature modules backed by shared `core:*` infrastructure (Koin DI, SQLDelight, OpenAPI, OAuth2/JWT in `core:security`).

```text
core/database/          migrations + user.sql (SQLDelight)
core/security/          OAuth2 configs, JWT validation
core/openapi/           DTOs + openapi.yaml route contracts

features/auth/
  AuthRouting.kt        POST /api/v1/auth/*, OAuth callbacks
  AuthService.kt        provider registry, session JWT issuance
  (no MQ for MVP)

features/user/
  UserRouting.kt        GET/PATCH /api/v1/users/*, blocks, admin trust
  UserService.kt        profiles, trust cache, blocks, peer ratings
  (TrustLedgerWriter)   internal contract for trade/validation callers
```

| Layer | Auth | User |
|-------|------|------|
| **Routing** | `AuthRouting.kt` | `UserRouting.kt` |
| **Service** | `AuthService.kt` | `UserService.kt` |
| **Integration** | — | — (MQ not used in MVP) |

REST surface: [api_index.md](./api_index.md). OpenAPI spec: `core/openapi/src/main/resources/openapi.yaml`.

---

## Step 1: Database Migration Setup

Define the tables to store user details, OAuth identities, sessions, rating details, and trust ledgers.

**Canonical DDL:** [schema.md](./schema.md#user--auth-module) in `core/database/src/main/resources/db/migration/000001_init.sql`.

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

## Step 2: Define SQLDelight Queries

Write the following database queries to `core/database/src/main/sqldelight/user.sq` to specify how data is inserted, updated, and lazily aggregated.

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
WHERE reviewee_id = ? AND created_at >= NOW() - INTERVAL '365 days';

-- name: CalculateUserTrustScore :one
SELECT COALESCE(100 + SUM(delta), 100)::integer AS trust_score
FROM user_trust_ledger
WHERE user_id = ?;
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
WHERE users.id = ? LIMIT 1;

-- name: UpsertUserStats :one
INSERT INTO user_stats (user_id, explicit_rating_avg, implicit_trust_score, last_calculated_at)
VALUES (?, ?, ?, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO UPDATE
SET explicit_rating_avg = EXCLUDED.explicit_rating_avg,
    implicit_trust_score = EXCLUDED.implicit_trust_score,
    last_calculated_at = CURRENT_TIMESTAMP
RETURNING *;

-- name: UpdateImplicitTrust :one
UPDATE user_stats
SET implicit_trust_score = implicit_trust_score + ?,
    last_calculated_at = CURRENT_TIMESTAMP
WHERE user_id = ?
RETURNING *;
```

### 3. Identity Provider and Audit Logs
```sql
-- name: FindUserByIdentity :one
SELECT users.id, users.username, users.created_at FROM users
JOIN user_identities ON users.id = user_identities.user_id
WHERE user_identities.provider = ? AND user_identities.provider_user_id = ? LIMIT 1;

-- name: LinkUserIdentity :one
INSERT INTO user_identities (user_id, provider, provider_user_id, email, provider_metadata)
VALUES (?, ?, ?, ?, ?)
RETURNING *;

-- name: AddTrustLedgerEntry :one
INSERT INTO user_trust_ledger (user_id, delta, event_type, description)
VALUES (?, ?, ?, ?)
RETURNING *;
```

Compile query methods by running SQLDelight generation from the repo root:
```bash
./gradlew :core:database:generateSqlDelightInterface
```

---

## Step 3: Ktor Feature Integration

Wire services via Koin (`features/auth/di`, `features/user/di`) and mount routes from `app/Application.kt`.

### 1. User Creation & Session Login (`features/auth/AuthService.kt`)

Upon user registration/login via OAuth (`POST /api/v1/auth/authenticate`):

1.  Check if the provider identity is linked (`FindUserByIdentity`).
2.  If it doesn't exist, create a new `User` record in a transaction.
3.  Issue a short-lived JWT session token (`SessionTokenLifetime`, **15 minutes**). Clients must re-call `POST /api/v1/auth/authenticate` on a ~15-minute cadence (or before `exp`) to obtain a fresh token; expired tokens are rejected by the Ktor `Authentication` plugin in `core:security`.
4.  **Crucial**: Immediately initialize the cache record inside `user_stats`:
    ```kotlin
    database.transaction {
        userQueries.upsertUserStats(
            userId = user.id,
            explicitRatingAvg = BigDecimal("5.00"),
            implicitTrustScore = 100,
        )
    }
    ```
5.  Link identity provider details by passing `provider` as a plain string parameter (e.g. `"google"` or `"apple"`).
6.  **Create `user_profiles` row on signup** via `CreateUserProfile` in the auth transaction.

`AuthRouting.kt` exposes:

| Route | Auth | Purpose |
|-------|------|---------|
| `GET /api/v1/auth/providers` | Public | List OAuth providers |
| `POST /api/v1/auth/authenticate` | Public | Exchange provider token → JWT |
| `GET /api/v1/auth/providers/{id}/account` | Public | Provider account metadata |
| `GET /api/v1/auth/callback/{provider}` | Public | OAuth redirect handler |
| `POST /api/v1/auth/providers/{id}/link` | Auth | Link provider to current user |
| `GET /api/v1/auth/providers/linked` | Auth | List linked identities |

### Auth policy

Full route matrix: [auth_and_permissions.md](./auth_and_permissions.md). Public profile reads are documented under seller/profile modules.

### 1b. Auth Provider Interface (`features/auth/provider`)

OAuth integrations are abstracted behind `AuthProvider`. Each provider (e.g. Google in `features/auth/provider/google/GoogleAuthProvider.kt`) registers via Koin:

```kotlin
// features/auth/di/AuthModule.kt
single<Map<String, AuthProvider>> {
    mapOf(
        "google" to get<GoogleAuthProvider>(),
        "apple" to get<AppleAuthProvider>(),
    )
}
```

Each provider implements:

- `verifyToken` — validate the upstream ID token and return a normalized `Identity`
- `getAccountInfo` / `canVerifyToken` — used by `GET /api/v1/auth/providers/{id}/account`
- `info` — metadata exposed through `GET /api/v1/auth/providers`
- `handleOAuthCallback` — HTTP OAuth redirect handler wired from `GET /api/v1/auth/callback/{provider}`

`AuthService` receives the provider map via Koin. Add a new provider by implementing `AuthProvider`, registering it in `AuthModule.kt`, and adding the route in `AuthRouting.kt`.

### 2. Lazy Recalculation Cache (`features/user/UserService.kt`)

When handling profile reads (`GET /api/v1/users/{id}/profile`, `GET /api/v1/users/me/profile`, etc.), evaluate if stats cache is stale (older than 1 hour):

```kotlin
var explicitRating = 5.0
var implicitScore = 100

val isStale = user.lastCalculatedAt?.let {
    Duration.between(it, Instant.now()) > Duration.ofHours(1)
} ?: true

if (isStale) {
    // 1. Recalculate rating with 365-day time decay cutoff
    explicitRating = userQueries.calculateUserRatingAvg(userId).executeAsOne()

    // 2. Sum trust scores from ledger history
    implicitScore = userQueries.calculateUserTrustScore(userId).executeAsOne()

    // 3. Upsert scores to cache
    userQueries.upsertUserStats(
        userId = userId,
        explicitRatingAvg = explicitRating.toBigDecimal().setScale(2, RoundingMode.HALF_UP),
        implicitTrustScore = implicitScore.toLong(),
    )
}
```

`UserRouting.kt` mounts profile, block, and admin routes. Request/response DTOs live in `core/openapi/`.

### 3. Trust scoring from other modules

When the [trade](./trade_module.md) and [validation](./validation_module.md) modules complete a handoff, they call **`UserService` via the `TrustLedgerWriter` Koin contract** (not public REST) to:

1. Insert `user_trust_ledger` with an `event_type` from [trust_events.md](./trust_events.md) (e.g. `TRADE_COMPLETED`).
2. Bump `user_stats.implicit_trust_score` in the same transaction.

`recordPeerRating` is internal today; optional public `POST /api/v1/users/{id}/ratings` route is Wave 3.5 in [implementation_plan.md](./implementation_plan.md).

Example pattern (implemented in validation/trade services, not a standalone file in `features/user`):

```kotlin
database.transaction {
    userQueries.addTrustLedgerEntry(
        userId = userId,
        delta = 15,
        eventType = "TRADE_COMPLETED",
        description = "Trade completed successfully",
    )
    userQueries.updateImplicitTrust(
        userId = userId,
        delta = 15,
    )
}
```

Register the cross-feature contract in Koin:

```kotlin
single<TrustLedgerWriter> { get<UserService>() }
```

---

## Implementation phases

### user-A — Core auth & identity ✅

- [x] Google OAuth + session JWT
- [x] Apple OAuth provider
- [x] `user_stats` seed on signup
- [x] Lazy trust/rating recalc (1h TTL)

### user-B — Blocks & admin ✅

- [x] `user_blocks` + `POST /api/v1/users/{id}/block` / `DELETE .../block`
- [x] `UpdateImplicitTrust` (admin allowlist)

### user-C — Peer ratings (partial) 🔶

- [x] `user_ratings` table + SQLDelight
- [x] Internal `recordPeerRating`
- [ ] Public `POST /api/v1/users/{id}/ratings` (Wave 3.5)

---

## Step 4: Verification

Reset the local Nix development PostgreSQL database schema and seed data to run tests:
```bash
# 1. Clean the postgres storage directory
# Reset local PostgreSQL if needed (Docker / local dev)
./gradlew test

# 3. Regenerate SQLDelight + OpenAPI
./gradlew :core:database:generateSqlDelightInterface
./gradlew :core:openapi:build

# 4. Execute feature test suites
./gradlew :features:auth:test :features:user:test

# 5. Full backend test run
./gradlew test
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `features/auth/src/main/kotlin/.../AuthRouting.kt` | OAuth + session HTTP routes |
| `features/auth/src/main/kotlin/.../AuthService.kt` | Provider registry, JWT issuance |
| `features/auth/src/main/kotlin/.../provider/` | Google, Apple `AuthProvider` impls |
| `features/auth/src/main/kotlin/.../di/AuthModule.kt` | Koin bindings |
| `features/auth/src/test/kotlin/...` | Auth integration tests |
| `features/user/src/main/kotlin/.../UserRouting.kt` | Profile, block, admin routes |
| `features/user/src/main/kotlin/.../UserService.kt` | Trust cache, blocks, ratings |
| `features/user/src/main/kotlin/.../di/UserModule.kt` | Koin bindings + `TrustLedgerWriter` |
| `features/user/src/test/kotlin/...` | User service tests |
| `core/database/src/main/sqldelight/user.sq` | SQLDelight queries |
| `core/database/src/main/resources/db/migration/000001_init.sql` | User/auth DDL |
| `core/security/src/main/kotlin/.../` | JWT validation, OAuth2 client configs |
| `core/openapi/src/main/resources/openapi.yaml` | REST contract (auth + user sections) |

---

## Related documentation

- [seller_profile_module.md](./seller_profile_module.md) — public profile REST routes on `features:user`
- [trust_events.md](./trust_events.md) — ledger event types
- [README.md](../README.md) — local setup
