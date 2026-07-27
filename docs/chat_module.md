# Guide: Chat Module (Stateful Group Chats)

**Real-time multi-person rooms** (2+ participants). MVP: tied to **trades**. Later: **group**-scoped rooms for communities.

**Status:** Doc complete · **Backend:** ⬜ · **Feature:** `features:chat`

**Depends on:** [trade_module.md](./trade_module.md), [user_module.md](./user_module.md) (blocks) · **Unblocks:** [moderation](./moderation_module.md) content reports, [groups](./groups_module.md) group chat

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 3.2 (`chat-A`, `chat-B`); group rooms Wave 4b · **Product:** [product_vision.md](./product_vision.md)

**Related:** [architecture.md](./architecture.md) · [clients.md](./clients.md) · [conventions.md](./conventions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Trade rooms** | One room per active trade (MVP) |
| **Group rooms** | One room per community group (groups-C) |
| **Multi-party** | 2+ participants; negotiation and complex orders |
| **Persistence** | Postgres messages; WS for delivery |
| **Block aware** | No delivery across blocks |
| **Thin clients** | Ordering, membership, validation on server |
| **Auth** | JWT on WS connect (same as REST) |

---

## Architecture

```text
Client A ──WS──► ChatHub ◄──WS── Client B
                  │
                  ├── ListMessages (REST, keyset)
                  ├── SendMessage (REST → fan-out WS)
                  └── Postgres chat_messages
```

Room created when trade → `accepted`. System messages optional for trade state changes.

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `ChatRouting.kt` | REST paths + WebSocket upgrade, OpenAPI metadata |
| Service | `ChatService.kt` | Rooms, messages, membership, block filtering |
| Integration | `ChatWebSocket.kt` | Coroutine per connection; fan-out on `Dispatchers.IO` |

Register in Koin (`chatModule`) and mount REST + WS routes from `Application.kt` via `configureChatRouting()`. Add and inject `BlockResolver` via Koin for delivery filtering when chat ships.

---

## Schema

| Table | Purpose |
|-------|---------|
| `chat_rooms` | Linked to `trade_id` |
| `chat_participants` | Membership |
| `chat_messages` | `room_id`, `sender_id`, body, `created_at` |

**Next migration:** `000006_chat.sql` — [schema.md](./schema.md#chat-module-planned--wave-3)

---

## REST / OpenAPI surface

| Operation | Auth |
|-----------|------|
| `GetRoom` | Auth |
| `ListMessages` | Auth, keyset |
| `SendMessage` | Auth |

WebSocket: subscribe to `room_id` after JWT handshake. [api_index.md](./api_index.md)

---

## Auth policy

Participants only; respect `user_blocks` via a `BlockResolver` contract (add when shipping). [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

### Phase chat-A — Persistence & REST

- [ ] Schema + `ChatService`
- [ ] `GetRoom`, `ListMessages`, `SendMessage`
- [ ] Auto-create room on trade accept (via `ChatRoomEnsurer` contract)
- [ ] Koin: `chatModule` + route mount in `Application.kt`
- [ ] Tests: `features/chat/src/test/kotlin/` — non-participant denied

### Phase chat-B — WebSocket gateway

- [ ] WS endpoint on Ktor (`install(WebSockets)`)
- [ ] Subscribe by `room_id`; push new messages
- [ ] Heartbeat / reconnect notes in [clients.md](./clients.md)
- [ ] Coroutine-per-connection fan-out

### Phase chat-C — Polish

- [ ] Typing indicators (optional)
- [ ] System messages for trade transitions
- [ ] MVP: no message edit

---

## Verification

```bash
./gradlew :core:database:generateSqlDelightInterface
./gradlew :features:chat:test
./gradlew test
# manual: two clients WS round-trip
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `core/database/src/main/resources/db/migration/000006_chat.sql` | Schema |
| `core/database/src/main/sqldelight/chat.sq` | SQLDelight queries |
| `core/openapi/src/main/kotlin/dto/` | OpenAPI DTOs |
| `features/chat/src/main/kotlin/.../ChatRouting.kt` | REST + WS routes |
| `features/chat/src/main/kotlin/.../ChatService.kt` | Business logic |
| `features/chat/src/main/kotlin/.../ChatWebSocket.kt` | WebSocket hub |
| `features/chat/src/test/kotlin/...` | Tests |
| `docs/chat_module.md` | This guide |

---

## Open questions

| Question | MVP default |
|----------|-------------|
| Message body format | Plain text MVP; markdown later |
| Max message length | 4 KB |
| Room after trade complete | Read-only archive |

---

## Related documentation

- [trade_module.md](./trade_module.md) — room lifecycle
- [moderation_module.md](./moderation_module.md) — report messages
