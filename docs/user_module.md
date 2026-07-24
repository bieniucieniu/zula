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
server/src/main/sqldelight/   migrations + user.sq (SQLDelight)
server/src/main/kotlin/core/security/   OAuth configs, JWT validation

features/auth/
  AuthRouting.kt        POST /api/auth/*, session refresh
  AuthService.kt        OAuth providers, JWT + refresh issuance

features/user/
  UserRouting.kt        GET/PATCH /api/users/*, blocks, admin trust
  UserService.kt        profiles, trust cache, blocks, peer ratings
```

| Layer | Auth | User |
|-------|------|------|
| **Routing** | `AuthRouting.kt` | `UserRouting.kt` |
| **Service** | `AuthService.kt` | `UserService.kt` |
| **Integration** | — | — (MQ not used in MVP) |

REST surface: [api_index.md](./api_index.md). OpenAPI: `/swagger` on running server.

---

## Step 1: Database Migration Setup

Define the tables to store user details, OAuth identities, sessions, rating details, and trust ledgers.

**Canonical DDL:** [schema.md](./schema.md#user--auth-module) in `server/src/main/sqldelight/com/zula/auth_schema.sq`.

Key tables: `users`, `user_profiles` (includes `location_tag`, `seller_headline`), `user_identities`, `user_stats`, `user_sessions`, `user_ratings`, `user_trust_ledger`, `user_blocks` (ratings/blocks/trust ledger planned in later migrations; same UUIDv7 conventions).

Example excerpt (see `auth_schema.sq` for full definitions):

```sql
-- 1. Base users (id assigned by DB)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    username TEXT NOT NULL UNIQUE
);

-- 2. User profiles (mutable display & localization)
CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name TEXT,
    avatar_url TEXT,
    bio TEXT,
    timezone TEXT,
    preferred_language TEXT,
    location_tag TEXT,
    seller_headline TEXT,
    updated_at BIGINT NOT NULL
);

-- 3. OAuth identities
CREATE TABLE user_identities (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    provider_user_id TEXT NOT NULL,
    email TEXT,
    provider_metadata TEXT,
    credentials_status TEXT NOT NULL DEFAULT 'missing',
    UNIQUE (provider, provider_user_id)
);

-- 4. Cached ratings/trust
CREATE TABLE user_stats (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    explicit_rating_avg REAL NOT NULL DEFAULT 5.0,
    implicit_trust_score BIGINT NOT NULL DEFAULT 100,
    last_calculated_at BIGINT NOT NULL
);

-- 5. Sessions (refresh rotation + JWT sid)
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    auth_method TEXT NOT NULL,
    refresh_token_hash TEXT UNIQUE,
    device_info TEXT,
    ip_address TEXT,
    expires_at BIGINT NOT NULL,
    is_revoked BIGINT NOT NULL DEFAULT 0,
    rotated_from_id UUID
);
```

Planned peer-rating, trust-ledger, and block tables use the same `UUID PRIMARY KEY DEFAULT uuidv7()` pattern and `UUID` foreign keys to `users(id)`.

---

## Step 2: Define SQLDelight Queries

Write database queries to `server/src/main/sqldelight/com/zula/user.sq`. **Do not pass `id` on insert** — use `RETURNING id`:

```sql
-- name: insertUser
INSERT INTO users (username)
VALUES (?)
RETURNING id;

-- name: insertIdentity
INSERT INTO user_identities (user_id, provider, provider_user_id, email, ...)
VALUES (?, ?, ?, ?, ...)
RETURNING id;

-- name: insertSession
INSERT INTO user_sessions (user_id, auth_method, refresh_token_hash, ...)
VALUES (?, ?, ?, ...)
RETURNING id;
```

Compile query methods by running SQLDelight generation from the repo root:
```bash
./gradlew :server:generateSqlDelightInterface
```

---

## Step 3: Ktor Feature Integration

Wire services via Koin (`features/auth/di`, `features/user/di`) and mount routes from `app/Application.kt`.

### 1. User Creation & Session Login (`features/auth/AuthService.kt`)

Upon user registration/login via OAuth (`POST /api/auth/authenticate` with provider `idToken`):

1. Verify the provider token (`AuthProvider.verify`).
2. Look up identity (`findUserByIdentity`); if missing, `insertUser` + `insertUserProfile` + `upsertUserStats` in one transaction — **ids come from `RETURNING id`**.
3. `insertSession` → DB-assigned session id (`sid` JWT claim).
4. Issue a short-lived access JWT (**15 minutes**). Clients refresh via `POST /api/auth/refresh` (refresh cookie or body) — not by re-running OAuth on every access-token expiry.
5. Link or update `user_identities` (`insertIdentity` / `updateIdentityLogin`).

**Not supported:** email OTP / `POST /api/auth/challenge`. Sign-in is OAuth-only (Google, Apple) plus optional dev bypass when configured.

`AuthRouting.kt` exposes:

| Route | Auth | Purpose |
|-------|------|---------|
| `GET /api/auth/providers` | Public | List OAuth providers |
| `POST /api/auth/authenticate` | Public | Exchange `idToken` → access JWT (+ refresh when issued) |
| `POST /api/auth/refresh` | Public | Rotate refresh token; new access JWT |
| `GET /api/auth/session` | Public / Auth | Session probe (Bearer or refresh cookie) |
| `POST /api/auth/logout` | Auth | Revoke session |
| `GET /api/auth/providers/linked` | Auth | List linked identities |

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

- `verify` — validate upstream `idToken` and return normalized `Identity`
- `info` — metadata for `GET /api/auth/providers`

`AuthService` receives the provider map via Koin. Add a new provider by implementing `AuthProvider`, registering it in `AuthModule.kt`, and adding the route in `AuthRouting.kt`.

### 2. Lazy Recalculation Cache (`features/user/UserService.kt`)

When handling profile reads (`GET /api/users/{id}/profile`, `GET /api/users/me/profile`, etc.), evaluate if stats cache is stale (older than 1 hour):

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

`UserRouting.kt` mounts profile, block, and admin routes. Request/response DTOs are defined in feature `domain` packages and exposed via Ktor OpenAPI.

### 3. Trust scoring from other modules

When the [trade](./trade_module.md) and [validation](./validation_module.md) modules complete a handoff, they call **`UserService` via a `TrustLedgerWriter` Koin contract** (add under `core/contracts` when those modules ship; not public REST) to:

1. Insert `user_trust_ledger` with an `event_type` from [trust_events.md](./trust_events.md) (e.g. `TRADE_COMPLETED`).
2. Bump `user_stats.implicit_trust_score` in the same transaction.

`recordPeerRating` is internal today; optional public `POST /api/users/{id}/ratings` route is Wave 3.5 in [implementation_plan.md](./implementation_plan.md).

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
// when validation/trade ship:
// single<TrustLedgerWriter> { get<UserService>() }
```

---

## Implementation phases

### user-A — Core auth & identity ✅

- [x] Google OAuth + session JWT + refresh
- [x] Apple OAuth provider
- [x] `user_stats` seed on signup
- [x] Lazy trust/rating recalc (1h TTL)

### user-B — Blocks & admin ✅

- [x] `user_blocks` + `POST /api/users/{id}/block` / `DELETE .../block`
- [x] `UpdateImplicitTrust` (admin allowlist)

### user-C — Peer ratings (partial) 🔶

- [x] `user_ratings` table + SQLDelight
- [x] Internal `recordPeerRating`
- [ ] Public `POST /api/users/{id}/ratings` (Wave 3.5)

---

## Step 4: Verification

Reset the local Nix development PostgreSQL database schema and seed data to run tests:
```bash
# 1. Clean the postgres storage directory
# Reset local PostgreSQL if needed (Docker / local dev)
./gradlew test

# 3. Regenerate SQLDelight
./gradlew :server:generateSqlDelightInterface

# 4. Execute server tests
./gradlew :server:test
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `server/src/main/kotlin/features/auth/AuthRouting.kt` | OAuth + session HTTP routes |
| `server/src/main/kotlin/features/auth/AuthService.kt` | Provider registry, JWT issuance |
| `server/src/main/kotlin/features/auth/provider/` | Google, Apple providers |
| `server/src/main/kotlin/features/auth/AuthModule.kt` | Koin bindings |
| `server/src/test/kotlin/features/auth/` | Auth tests |
| `server/src/main/kotlin/features/user/UserRouting.kt` | Profile, block, admin routes |
| `server/src/main/kotlin/features/user/UserService.kt` | Trust cache, blocks, ratings |
| `server/src/main/sqldelight/com/zula/user.sq` | SQLDelight queries |
| `server/src/main/sqldelight/com/zula/auth_schema.sq` | User/auth DDL |
| `server/src/main/kotlin/core/security/` | JWT validation, OAuth configs |

---

## Related documentation

- [seller_profile_module.md](./seller_profile_module.md) — public profile REST routes on `features:user`
- [trust_events.md](./trust_events.md) — ledger event types
- [README.md](../README.md) — local setup
