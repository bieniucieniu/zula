# Auth and permissions

REST auth is enforced by Ktor `Authentication` plugin (`core:security`). Routes **not** listed as public require a valid Bearer JWT in the `Authorization` header.

WebSocket chat (planned) uses the same JWT at connect time.

---

## Auth levels

| Level | Meaning |
|-------|---------|
| **Public** | No token; optional token enriches viewer-specific fields |
| **Auth** | Valid session JWT; actor = token user |
| **Admin** | Auth + env allowlist (e.g. Google subject ID) |
| **Internal** | Not exposed on public REST; service-to-service only |

---

## AuthService

| RPC | Level | Notes |
|-----|-------|-------|
| `GetAuthProviders` | Public | Configured OAuth providers (Google, Apple) |
| `Authenticate` | Public | Exchange provider `idToken` or auth `code` (+ PKCE) → access JWT (+ optional refresh token) |
| `Refresh` | Public | Rotate refresh token; issue new access JWT |
| `GetSession` | Public / Auth | Optional Bearer JWT; else refresh cookie → session probe (may rotate cookies) |
| `Logout` | Auth | Revoke session by `sid` and/or refresh token |
| `ListLinkedProviders` | Auth | Own linked identities only |
| `StartGoogleOAuth` | Public | Browser redirect/popup start (`/auth/oauth/google/start`) |
| `GoogleOAuthCallback` | Public | Server `/auth/callback/google` — sets cookies; popup `postMessage` or redirect `/oauth/complete` |

**Removed:** email OTP / magic-link login (`POST /auth/challenge` and `provider: email_otp`). Sign-in is OAuth-only (plus optional dev bypass when `AUTH_DEV_BYPASS_SECRET` is set). Leftover `auth_challenges` storage / `extendOAuthSession` helpers were also removed from the schema and repository.

**Clients:** web uses backend Google popup → cookies; native uses PKCE/`authenticate` → bearer. See [clients.md](./clients.md).

---

## GoogleService

| RPC | Level | Notes |
|-----|-------|-------|
| `GetGoogleAccountInfo` | Public | |

---

## UserService

| RPC | Level | Ownership / notes |
|-----|-------|-------------------|
| `GetUserProfile` | Public | Legacy; prefer `GetSellerProfile` |
| `GetUserByUsername` | Public | Legacy trust-only response |
| `GetSellerProfile` | Public | Block policy A when viewer authenticated |
| `ListSellerReviews` | Public | |
| `GetProfileReadme` | Public | |
| `ListPortfolioItems` | Public | Public items only |
| `ListPublicActivity` | Public | Stub until feed ships |
| `GetMyProfile` | Auth | Own profile only; includes `is_admin` when caller’s linked Google ID matches `ADMIN_GOOGLE_ID` |
| `UpdateMyProfile` | Auth | Own row only |
| `UpdateProfileReadme` | Auth | Own readme |
| `ListPortfolioItems` (owner view) | Auth | Includes unlisted when querying self |
| `UpsertPortfolioItem` | Auth | Own portfolio |
| `DeletePortfolioItem` | Auth | Own items |
| `PinPortfolioItem` / `UnpinPortfolioItem` / `ReorderProfilePins` | Auth | Max 6 pins |
| `BlockUser` / `UnblockUser` | Auth | Viewer = blocker |
| `UpdateImplicitTrust` | Admin | Env Google ID whitelist |

### Block behavior

**Policy: strict hide** — authenticated viewer blocked either direction → `NotFound` on all public profile reads.

Central helper: `enforcePublicTargetAccess` in `UserService` (when shipped); feed/chat will inject a `BlockResolver` contract via Koin once those features need it.

| RPC | When blocked (authenticated viewer) |
|-----|--------------------------------------|
| `GetSellerProfile` | `NotFound` |
| `GetProfileReadme` | `NotFound` |
| `ListPortfolioItems` | `NotFound` |
| `ListSellerReviews` | `NotFound` |
| `ListPublicActivity` | `NotFound` |
| `ListFeedByAuthor` *(planned)* | Exclude blocked authors/items |
| Chat *(planned)* | No delivery across block |

---

## FeedService *(planned)*

| RPC | Level | Notes |
|-----|-------|-------|
| `ListForYouFeed` | Auth | Personalized |
| `ListFeedByAuthor` | Public | Optional auth for block filter |
| `ListFeedByTrait` | Public | |
| `GetFeedItem` | Public | |
| `CreateFeedItem` | Auth | Author = token user |
| `UpdateFeedItem` / `DeleteFeedItem` | Auth | Owner only |
| `FollowTrait` / `UnfollowTrait` | Auth | |

---

## MediaService *(planned)*

| RPC | Level | Notes |
|-----|-------|-------|
| `RequestUpload` | Auth | Presigned PUT for caller |
| `RequestAvatarUpload` | Auth | Same; key scoped to user |

---

## GeolocationService *(planned)*

| RPC | Level | Notes |
|-----|-------|-------|
| `ReportNetworkFingerprint` | Auth | Rate limited |

---

## TradeService *(planned)*

| RPC | Level | Notes |
|-----|-------|-------|
| `CreateTrade` | Auth | Participant |
| `AcceptTrade` / `CancelTrade` | Auth | Counterparty / participant rules |
| `GetTrade` | Auth | Participants only |
| `ProposeMeetup` / `ConfirmMeetup` | Auth | Participants |

---

## ValidationService *(planned)*

| RPC | Level | Notes |
|-----|-------|-------|
| `GenerateHandoffCode` | Auth | Trade participant |
| `VerifyHandoff` | Auth | Participant |

---

## ChatService *(planned)*

| RPC | Level | Notes |
|-----|-------|-------|
| `GetRoom` / `ListMessages` / `SendMessage` | Auth | Room participant |
| WebSocket subscribe | Auth | JWT at handshake |

---

## ModerationService *(planned)*

| RPC | Level | Notes |
|-----|-------|-------|
| `ReportUser` / `ReportFeedItem` | Auth | Not self |
| `ListPendingReports` / `ResolveReport` | Admin | Same pattern as `UpdateImplicitTrust` |

---

## Session lifetime

| Token | TTL | Refresh |
|-------|-----|---------|
| Access JWT | **15 minutes** (`JWT_ACCESS_TOKEN_TTL_SECONDS`, default 900) | `POST /api/auth/refresh` or `GET /api/auth/session` with refresh cookie |
| Refresh token | **30 days** (`JWT_REFRESH_TOKEN_TTL_SECONDS`, default 2592000) | Rotated on each refresh |

Clients obtain the initial session via OAuth (web backend popup or native `POST /api/auth/authenticate`). They must refresh before access JWT expiry — not re-run the OAuth flow on every 15-minute window.

---

## Auth hardening (current)

| Behavior | Rule |
|----------|------|
| JWT validate | Crypto OK **and** active DB session for `sid` + subject; revoked/missing session ⇒ no principal (unauthenticated) |
| `GET /auth/session` | Optional JWT; if missing, refresh cookie may rotate tokens (SPA bootstrap) |
| Session `email` | From JWT `email` claim or linked identity — **not** username |
| Logout | Soft provider credential check; local session always revoked; logout does not fail the client |
| Google refresh revoke | Only on OAuth `invalid_grant` (not network/transient errors) |
| Dev bypass | Fixed `dev@zula.local` only when `AUTH_DEV_BYPASS_SECRET` set |
| OAuth nonce | Server Google start stores nonce cookie; verified against id_token |
| Identity link | Reject if provider identity already owned by another user; update scopes on login |
| Username allocate | Retry on unique violation (race-safe) |
| Public URL / JWT iss | Same Forwarded/Host policy via `publicBaseUrl` for OAuth callbacks and JWT issuer |
| Cookie Secure | `security.cookies.secure` / `AUTH_COOKIE_SECURE` |
| OIDC JWKS cache | Refresh replaces map (TTL 1h); rotated kids dropped |

---

## Related

- [user_module.md](./user_module.md) — signup creates `user_profiles` + `user_stats`
- [conventions.md](./conventions.md) — thin-client rules
- [architecture.md](./architecture.md) — service boundaries
- [clients.md](./clients.md) — web vs native sign-in
