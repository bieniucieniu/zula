# Auth and permissions

REST auth is enforced in HTTP middleware (`apps/backend/internal/api/`). Routes **not** listed as public require a valid Bearer JWT in the `Authorization` header.

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
| `GetAuthProviders` | Public | |
| `Authenticate` | Public | Returns JWT |
| `GetProviderAccountInfo` | Public | |
| `LinkProvider` | Auth | Recent session (~10m); links verified provider identity to JWT user |
| `ListLinkedProviders` | Auth | Own linked identities only |
| `StartLinkProvider` | Auth | Recent session (~10m); returns OAuth URL with signed link intent in `state` |

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

Central helper: `enforcePublicTargetAccess` in UserService; feed/chat use `app.ModuleAPI.Blocks.ResolveViewerBlock`.

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

JWT lifetime: **15 minutes** (`auth.SessionTokenLifetime`). Clients refresh via `Authenticate` before expiry.

---

## Related

- [user_module.md](./user_module.md) — signup creates `user_profiles` + `user_stats`
- [conventions.md](./conventions.md) — thin-client rules
- [architecture.md](./architecture.md) — service boundaries
