# REST API index

Quick reference for HTTP routes under `/api`. OpenAPI is generated from Ktor route metadata at runtime (`/swagger`). Auth: [auth_and_permissions.md](./auth_and_permissions.md).

> Legacy RPC names below map to REST paths; prefer the OpenAPI spec for client generation.

Request/response shapes: feature `domain` DTOs + Ktor OpenAPI (`/swagger`).

**Legend:** ✅ implemented · 🔜 planned

---

## AuthService ✅

| RPC | Auth | HTTP |
|-----|------|------|
| `GetAuthProviders` | Public | `GET /api/auth/providers` |
| `Authenticate` | Public | `POST /api/auth/authenticate` |
| `Refresh` | Public | `POST /api/auth/refresh` |
| `GetSession` | Public / Auth | `GET /api/auth/session` |
| `Logout` | Auth | `POST /api/auth/logout` |
| `ListLinkedProviders` | Auth | `GET /api/auth/providers/linked` |

OAuth-only login (Google, Apple `idToken`). No email OTP challenge flow.

---

## GoogleService ✅

| RPC | Auth |
|-----|------|
| `GetGoogleAccountInfo` | Public |

---

## UserService ✅

| RPC | Auth |
|-----|------|
| `GetUserProfile` | Public (legacy) |
| `GetUserByUsername` | Public (legacy) |
| `GetSellerProfile` | Public |
| `GetMyProfile` | Auth |
| `UpdateMyProfile` | Auth |
| `ListSellerReviews` | Auth optional |
| `BlockUser` / `UnblockUser` | Auth |
| `GetProfileReadme` | Public |
| `UpdateProfileReadme` | Auth |
| `ListPortfolioItems` | Public |
| `UpsertPortfolioItem` / `DeletePortfolioItem` | Auth |
| `PinPortfolioItem` / `UnpinPortfolioItem` / `ReorderProfilePins` | Auth |
| `ListPublicActivity` | Public (stub until feed) |
| `UpdateImplicitTrust` | Admin |

**Internal (not gRPC):** `RecordPeerRating` — called by validation module.

---

## FeedService 🔜

| RPC | Auth |
|-----|------|
| `CreateFeedItem` | Auth |
| `GetFeedItem` | Public |
| `ListForYouFeed` | Auth |
| `ListFeed` | Public |
| `ListFeedByAuthor` | Public |
| `ListFeedByTrait` | Public |
| `FollowTrait` / `UnfollowTrait` | Auth |

Doc: [feed_module.md](./feed_module.md)

---

## MediaService 🔜

| RPC | Auth |
|-----|------|
| `RequestUpload` | Auth |
| `RequestAvatarUpload` | Auth |

Doc: [media_module.md](./media_module.md)

---

## GeolocationService 🔜

| RPC | Auth |
|-----|------|
| `ReportNetworkFingerprint` | Auth |

Doc: [geolocation_module.md](./geolocation_module.md)

---

## TradeService 🔜

| RPC | Auth |
|-----|------|
| `CreateTrade` | Auth |
| `AcceptTrade` / `CancelTrade` | Auth |
| `GetTrade` | Auth |
| `ProposeMeetup` / `ConfirmMeetup` | Auth |

Doc: [trade_module.md](./trade_module.md)

---

## ValidationService 🔜

| RPC | Auth |
|-----|------|
| `GenerateHandoffCode` | Auth |
| `VerifyHandoff` | Auth |

Doc: [validation_module.md](./validation_module.md)

---

## ChatService 🔜

| RPC | Auth |
|-----|------|
| `GetRoom` | Auth |
| `ListMessages` | Auth |
| `SendMessage` | Auth |

WebSocket: room subscription (same JWT). Doc: [chat_module.md](./chat_module.md)

---

## ModerationService 🔜

| RPC | Auth |
|-----|------|
| `ReportUser` | Auth |
| `ReportFeedItem` | Auth |
| `ListPendingReports` | Admin |
| `ResolveReport` | Admin |

Doc: [moderation_module.md](./moderation_module.md)

---

## Shared messages

| Message | File | Use |
|---------|------|-----|
| `RichDocument` | `document` | Markdown bodies |
| `ProfileCursor` | `document` | Reviews, activity lists |
| `PortfolioCursor` | `document` | Portfolio pagination |
| `FeedCursor` | `feed` *(planned)* | All feed list endpoints |

Pagination rules: [conventions.md](./conventions.md)

---

## Related

- [architecture.md](./architecture.md) — service ownership
- [clients.md](./clients.md) — code generation per platform
