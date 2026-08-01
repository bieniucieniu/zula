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

| RPC | Auth | HTTP |
|-----|------|------|
| `GetUserProfile` | Public (legacy) | `GET /api/users/{id}/profile` |
| `GetUserByUsername` | Public (legacy) | `GET /api/users/{username}` |
| `GetSellerProfile` | Public | `GET /api/sellers/{idOrUsername}` |
| `GetMyProfile` | Auth | `GET /api/users/me/profile` |
| `UpdateMyProfile` | Auth | `PATCH /api/users/me/profile` (markdown `bio` + optional `bioExpectedRevision`) |
| `ListSellerReviews` | Auth optional | `GET /api/reviews/seller/{userId}` |
| `BlockUser` / `UnblockUser` | Auth | `POST` / `DELETE /api/users/{userId}/block` |
| `ListPortfolioItems` | Public | `GET /api/users/{idOrMe}/portfolio` |
| `UpsertPortfolioItem` / `DeletePortfolioItem` | Auth | `POST` / `DELETE /api/users/me/portfolio/items…` |
| `PinPortfolioItem` / `UnpinPortfolioItem` / `ReorderProfilePins` | Auth | `/api/users/me/portfolio/pins…` |
| `ListPublicActivity` | Public | `GET /api/users/{idOrMe}/activity` (empty stub until feed) |
| `UpdateImplicitTrust` | Admin | `PATCH /api/admin/users/{id}/trust` |

**Internal (not REST):** `RecordPeerRating` — called by validation module.

`kind=offer` portfolio items rejected until feed ships. `feed_item_id` / `trade_id` columns exist without FKs.
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
| `LikeFeedItem` / `UnlikeFeedItem` | Auth | like = bump |
| `ListComments` / `AddComment` | Public list / Auth write |
| `BookmarkFeedItem` / `UnbookmarkFeedItem` | Auth |
| `ListBookmarks` | Auth |
| `FollowTrait` / `UnfollowTrait` | Auth |

Doc: [feed_module.md](./feed_module.md)

---

## MediaService ✅

| RPC | Auth | HTTP |
|-----|------|------|
| `UploadMedia` | Auth | `POST /api/media/uploads` (raw image body) |
| `GetMediaObject` | Public | `GET /api/media/objects/{key…}` |
| `RequestAvatarUpload` | Auth | 🔜 |

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
| `SetLocationMode` / `SetFulfillmentPlace` | Auth |
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
| `SendMessage` | Auth (`clientMessageId` idempotency) |

SSE: `GET /api/events/stream` (`text/event-stream`, JWT; `Last-Event-ID` catch-up). Doc: [chat_module.md](./chat_module.md)

---

## GroupService 🔜

| RPC | Auth |
|-----|------|
| `CreateGroup` | Auth |
| `GetGroup` | Public / member |
| `UpdateGroup` | Owner/admin |
| `JoinGroup` / `LeaveGroup` | Auth |
| `ListGroupMembers` | Member |

Group feed: `ListFeedByGroup` on FeedService. Doc: [groups_module.md](./groups_module.md)

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
| `ProfileCursor` | `document` | Reviews, portfolio, public activity |
| `FeedCursor` | `feed` *(planned)* | All feed list endpoints |

Pagination rules: [conventions.md](./conventions.md)

---

## Related

- [architecture.md](./architecture.md) — service ownership
- [clients.md](./clients.md) — code generation per platform
