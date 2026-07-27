# Guide: Groups Module (Communities)

**Named communities** for import, resell, craft, and local services — the Facebook sales-group job, inside Zula: shared posts, membership, and multi-party chat.

**Status:** Doc complete · **Backend:** ⬜ · **Feature:** `features:groups` (planned)

**Depends on:** [user_module.md](./user_module.md), [feed_module.md](./feed_module.md) · **Unblocks:** group-scoped chat, group feed filters · **Product:** [product_vision.md](./product_vision.md)

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 4b

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#groups-module-planned) · [chat_module.md](./chat_module.md) · [conventions.md](./conventions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Groups** | Create / join / leave communities with slug, title, description, visibility |
| **Membership** | Roles: `owner`, `admin`, `member`; invite or open join |
| **Group feed** | Optional `group_id` on `feed_items` — list by group; same offer/need/trip kinds |
| **Group chat** | Multi-party room (2+) bound to `group_id` via chat feature |
| **Product fit** | Import / resell / craft circles — not casual single-item classifieds ([product_vision.md](./product_vision.md)) |
| **Safety** | Honor `user_blocks`; reports via moderation; owners can remove posts/members |

---

## Architecture

```text
Client
  │ REST
  ▼
GroupRouting.kt
  │
  ▼
GroupService.kt
  ├─ groups / group_members CRUD
  ├─ enforce membership on writes
  └─ ChatRoomEnsurer (group room) when chat ships

FeedService
  └─ ListFeedByGroup / CreateFeedItem(group_id=…) when membership OK
```

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `GroupRouting.kt` | HTTP paths, OpenAPI metadata |
| Service | `GroupService.kt` | Membership, visibility, moderation hooks |
| Integration | `GroupPublisher.kt` / `GroupConsumer.kt` | Optional JobRunr (invite emails later) |

Register in Koin (`groupsModule`) and mount under `/api` from `app/Routing.kt`. Cross-feature: inject ports when a second caller exists (`ChatRoomEnsurer`, feed membership check) — see [architecture.md](./architecture.md#cross-feature-internal-api).

---

## Schema

Canonical index: [schema.md](./schema.md#groups-module-planned).

| Table | Purpose |
|-------|---------|
| `groups` | `id`, `slug`, `title`, `description`, `visibility` (`public` / `private`), `created_by` |
| `group_members` | `(group_id, user_id)`, `role`, `joined_at` (UUIDv7 id optional) |

**Feed linkage (feed migration or follow-up):** nullable `feed_items.group_id` → `groups(id)` ON DELETE SET NULL.

**Chat linkage:** `chat_rooms.group_id` nullable (alongside `trade_id`) — [chat_module.md](./chat_module.md).

**Next migration:** `000009_groups.sql` (after moderation numbering in plan; renumber on merge if needed).

---

## REST / OpenAPI surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/groups` | Auth | Create group (creator = owner) |
| `GET` | `/api/groups/{idOrSlug}` | Public / auth | Group header; private → members only |
| `PATCH` | `/api/groups/{id}` | Owner/admin | Update metadata |
| `POST` | `/api/groups/{id}/join` | Auth | Join open group / accept invite |
| `DELETE` | `/api/groups/{id}/members/me` | Auth | Leave |
| `GET` | `/api/groups/{id}/members` | Member | Keyset member list |
| `GET` | `/api/feed/by-group/{id}` | Per group visibility | Group feed (owned by **feed** feature) |

DTOs live in feature `domain` packages + Ktor OpenAPI. Index: [api_index.md](./api_index.md).

---

## Auth policy

| Route | Auth | Notes |
|-------|------|-------|
| Public group read | Public | Optional JWT for membership flags |
| Private group read | Member | Else `404` |
| Create / join / leave | Auth | — |
| Admin member remove | Owner/admin | — |
| Create feed in group | Auth + member | FeedService checks membership |

Full matrix: [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

### Phase groups-A — Schema & CRUD

- [ ] `000009_groups.sql` + SQLDelight
- [ ] Create / get / patch group; join / leave
- [ ] Tests: slug unique, private hide, owner role

### Phase groups-B — Feed linkage

- [ ] `feed_items.group_id`; `GET /api/feed/by-group/{id}`
- [ ] CreateFeedItem rejects non-members
- [ ] Enforce product rule (no casual single-item spam) via same feed validation

### Phase groups-C — Group chat

- [ ] `chat_rooms.group_id`; ensure room on first message or on create
- [ ] Multi-party send/list; block filtering
- [ ] Depends on [chat_module.md](./chat_module.md) chat-A+

### Phase groups-D — Moderation hooks

- [ ] Remove member / remove post
- [ ] Report targets for group content ([moderation_module.md](./moderation_module.md))

---

## Verification

```bash
./gradlew :server:generateSqlDelightInterface
./gradlew :server:test
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `server/.../features/groups/GroupRouting.kt` | HTTP |
| `server/.../features/groups/GroupService.kt` | Domain |
| `server/.../features/groups/GroupModule.kt` | Koin |
| `server/src/main/sqldelight/com/zula/*.sq` | Queries |
| `server/.../features/groups/` tests | Membership / visibility |

Scaffolding may already exist as empty feature packs — fill when shipping groups-A.

---

## Open questions

| Question | MVP default |
|----------|-------------|
| Invites only vs open join | Open join for `public`; invite for `private` |
| Max members | Soft limit 500 until metrics say otherwise |
| Discovery list of groups | `GET /api/groups` search later; MVP = link / slug |

---

## Related documentation

- [product_vision.md](./product_vision.md) — why groups exist
- [feed_module.md](./feed_module.md) — group-scoped listings
- [chat_module.md](./chat_module.md) — multi-party rooms
- [moderation_module.md](./moderation_module.md) — reports
