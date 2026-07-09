# Guide: Chat Module (Stateful Group Chats)

**Real-time multi-person rooms** tied to trades (MVP), coordinated via **WebSocket** goroutines.

**Status:** Doc complete · **Backend:** ⬜ · **Service:** `ChatService` (planned)

**Depends on:** [trade_module.md](./trade_module.md), [user_module.md](./user_module.md) (blocks) · **Unblocks:** [moderation](./moderation_module.md) content reports

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 3.2 (`chat-A`, `chat-B`)

**Related:** [architecture.md](./architecture.md) · [clients.md](./clients.md) · [conventions.md](./conventions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Trade rooms** | One room per active trade |
| **Persistence** | Postgres messages; WS for delivery |
| **Block aware** | No delivery across blocks |
| **Thin clients** | Ordering, membership, validation on server |
| **Auth** | JWT on WS connect (same as gRPC) |

---

## Architecture

```text
Client A ──WS──► ChatHub ◄──WS── Client B
                  │
                  ├── ListMessages (gRPC, keyset)
                  ├── SendMessage (gRPC → fan-out WS)
                  └── Postgres chat_messages
```

Room created when trade → `accepted`. System messages optional for trade state changes.

---

## Schema

| Table | Purpose |
|-------|---------|
| `chat_rooms` | Linked to `trade_id` |
| `chat_participants` | Membership |
| `chat_messages` | `room_id`, `sender_id`, body, `created_at` |

**Next migration:** `000006_chat.up.sql` — [schema.md](./schema.md#chat-module-planned--wave-3)

---

## Proto / RPC surface

| RPC | Auth |
|-----|------|
| `GetRoom` | Auth |
| `ListMessages` | Auth, keyset |
| `SendMessage` | Auth |

WebSocket: subscribe to `room_id` after JWT handshake. [api_index.md](./api_index.md)

---

## Auth policy

Participants only; respect `user_blocks`. [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

### Phase chat-A — Persistence & gRPC

- [ ] Schema + `ChatService`
- [ ] `GetRoom`, `ListMessages`, `SendMessage`
- [ ] Auto-create room on trade accept
- [ ] Tests: `apps/backend/tests/chat/` — non-participant denied

### Phase chat-B — WebSocket gateway

- [ ] WS endpoint on HTTP server
- [ ] Subscribe by `room_id`; push new messages
- [ ] Heartbeat / reconnect notes in [clients.md](./clients.md)
- [ ] Goroutine-per-connection fan-out

### Phase chat-C — Polish

- [ ] Typing indicators (optional)
- [ ] System messages for trade transitions
- [ ] MVP: no message edit

---

## Verification

```bash
devenv test
cd apps/backend && go test ./tests/chat/...
# manual: two clients WS round-trip
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `apps/backend/db/migration/000006_chat.up.sql` | Schema |
| `apps/backend/internal/apitypes/` | gRPC |
| `apps/backend/internal/service/chat.go` | Service |
| `apps/backend/internal/ws/` | WebSocket hub |
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
