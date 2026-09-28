# Guide: Chat Module

**Not part of the starter kit.** See [product_vision.md](./product_vision.md).

Multi-person rooms (2+ participants) over SSE. Send via REST.

**Transport:** **SSE** (Server-Sent Events) for inbound delivery — not WebSockets. Clients **send** via REST; server **pushes** via `GET /api/events/stream`.

**Status:** Doc complete · **Backend:** ✅ A–B · **Feature:** `features:chat`

**Depends on:** [trade_module.md](./trade_module.md), [user_module.md](./user_module.md) (blocks) · **Unblocks:** [moderation](./moderation_module.md) content reports, [groups](./groups_module.md) group chat

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 3.2 (`chat-A` / `chat-B`); group rooms Wave 3.3 (`groups-C`, **MVP**) · **Product:** [product_vision.md](./product_vision.md)

**Related:** [architecture.md](./architecture.md) · [clients.md](./clients.md) · [conventions.md](./conventions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Trade rooms** | One room per active trade (MVP) |
| **Group rooms** | One room per community group (groups-C) |
| **Multi-party** | 2+ participants; negotiation and complex orders |
| **Persistence** | Postgres messages; **SSE** for delivery |
| **Idempotency** | `clientMessageId` on `SendMessage` — dedupe retries |
| **Block aware** | No delivery across blocks |
| **Thin clients** | Ordering, membership, validation on server |
| **Auth** | JWT on SSE connect (same as REST; query or `Authorization` header) |

---

## Architecture

```text
Client A ──REST SendMessage──► ChatService ──SSE fan-out──► Client B
                                      │
                                      ├── ListMessages (REST, keyset)
                                      ├── GET /api/events/stream (text/event-stream)
                                      └── Postgres chat_messages
```

Room created when trade → `accepted`. System messages optional for trade state changes.

**SSE events (MVP + reserved):**

| Event | When |
|-------|------|
| `MESSAGE_RECEIVED` | New chat message |
| `MESSAGE_READ` | Read receipt (optional polish) |
| `TRADE_STATE_CHANGED` | Trade transition |
| `PAYMENT_SUCCESS` / `PAYMENT_FAILED` | **Reserved** — post-MVP payment wave |

Support `Last-Event-ID` for catch-up after reconnect.

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `ChatRouting.kt` | REST paths + SSE stream route, OpenAPI metadata |
| Service | `ChatService.kt` | Rooms, messages, membership, block filtering |
| Integration | `EventStreamer.kt` | Pub-sub → SSE connections; `Last-Event-ID` replay buffer |

Register in Koin (`chatModule`) and mount REST + SSE routes from `Application.kt` via `configureChatRouting()`. Add and inject `BlockResolver` via Koin for delivery filtering when chat ships.

---

## Schema

| Table | Purpose |
|-------|---------|
| `chat_rooms` | Linked to `trade_id` (and later `group_id`) |
| `chat_participants` | Membership |
| `chat_messages` | `room_id`, `sender_id`, body, `client_message_id`, `created_at` |
| `sse_event_log` *(optional)* | Durable event id + payload for `Last-Event-ID` replay |

**Next migration:** `000006_chat.sql` — [schema.md](./schema.md#chat-module-planned--wave-3)

---

## REST / OpenAPI surface

| Operation | Auth |
|-----------|------|
| `GetRoom` | Auth |
| `ListMessages` | Auth, keyset |
| `SendMessage` | Auth; body/header `clientMessageId` |

SSE: `GET /api/events/stream` — `Accept: text/event-stream`, JWT required. [api_index.md](./api_index.md)

---

## Auth policy

Participants only; respect `user_blocks` via a `BlockResolver` contract (add when shipping). [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

### Phase chat-A — Persistence & REST

- [ ] Schema + `ChatService`
- [ ] `GetRoom`, `ListMessages`, `SendMessage` with `clientMessageId` idempotency
- [ ] Auto-create room on trade accept (via `ChatRoomEnsurer` contract)
- [ ] Koin: `chatModule` + route mount in `Application.kt`
- [ ] Tests: `features/chat/src/test/kotlin/` — non-participant denied; duplicate `clientMessageId` → same message

### Phase chat-B — SSE streamer

- [ ] `GET /api/events/stream` (`text/event-stream`)
- [ ] Fan-out `MESSAGE_RECEIVED` / `TRADE_STATE_CHANGED`
- [ ] `Last-Event-ID` replay (in-memory ring or `sse_event_log`)
- [ ] Reconnect notes in [clients.md](./clients.md)
- [ ] No WebSocket endpoint

### Phase chat-C — Polish

- [ ] Typing indicators (optional — may stay out of SSE MVP)
- [ ] System messages for trade transitions
- [ ] `MESSAGE_READ` events
- [ ] MVP: no message edit

---

## Verification

```bash
./gradlew :core:database:generateSqlDelightInterface
./gradlew :features:chat:test
./gradlew test
# manual: two clients — REST send → SSE receive; reconnect with Last-Event-ID
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `core/database/src/main/resources/db/migration/000006_chat.sql` | Schema |
| `core/database/src/main/sqldelight/chat.sq` | SQLDelight queries |
| `core/openapi/src/main/kotlin/dto/` | OpenAPI DTOs |
| `features/chat/src/main/kotlin/.../ChatRouting.kt` | REST + SSE routes |
| `features/chat/src/main/kotlin/.../ChatService.kt` | Business logic |
| `features/chat/src/main/kotlin/.../EventStreamer.kt` | SSE hub |
| `features/chat/src/test/kotlin/...` | Tests |
| `docs/chat_module.md` | This guide |

---

## Open questions

| Question | MVP default |
|----------|-------------|
| Message body format | Plain text MVP; markdown later |
| Max message length | 4 KB |
| Room after trade complete | Read-only archive |
| SSE auth on browsers | Prefer `Authorization` header; cookie session OK for web |

---

## Related documentation

- [trade_module.md](./trade_module.md) — room lifecycle
- [moderation_module.md](./moderation_module.md) — report messages
