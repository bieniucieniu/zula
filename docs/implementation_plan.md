# Zula — Cross-Module Implementation Plan

Master rollout plan for modules **not yet implemented** in the backend, plus **remaining phases** on partially shipped modules.

Empty feature packages (`chat`/`feed`/… Module+Service+Publisher+Consumer+Routing stubs) may already exist as scaffolding. Cross-feature `core/contracts` ports are **deferred until a second caller ships** — see [architecture.md](./architecture.md#cross-feature-internal-api) and [cleanup_impl_plan.md](./cleanup_impl_plan.md).

**Product framing:** [product_vision.md](./product_vision.md)

**Related:** [README.md](../README.md) · [docs index](./README.md) · [architecture](./architecture.md) · [conventions](./conventions.md) · per-module guides in `docs/*_module.md`

---

## Current state (2026-08)

| Module | Doc | Backend | Notes |
|--------|-----|---------|-------|
| [user](./user_module.md) | Full | ✅ MVP | Auth, trust, blocks, profiles |
| [seller_profile](./seller_profile_module.md) | Full | ✅ A–B | Listings tab blocked on feed; seller embeds readme/pins |
| [profile_portfolio](./profile_portfolio_module.md) | Full | ✅ A–B | Docs/readme/portfolio/pins in `0.sqm`; `offer` + activity blocked on feed |
| [feed](./feed_module.md) | Full | ⬜ | **MVP critical** — offers / needs / trips + like/bump, comments, bookmarks |
| [traits](./traits_module.md) | Plan | ⬜ | Can ship inside feed Phase A |
| [groups](./groups_module.md) | Plan | ⬜ | **MVP critical** — communities + group feed (Wave 1) |
| [media](./media_module.md) | Plan | ⬜ | MinIO in local dev only |
| [geolocation](./geolocation_module.md) | Plan | ⬜ | Fingerprint `location_tag`; complements trade location modes |
| [trade](./trade_module.md) | Plan | ⬜ | `swap` / `meetup_cash` + location matrix; payment prep only (no rails) |
| [validation](./validation_module.md) | Plan | ⬜ | Trust ledger exists |
| [chat](./chat_module.md) | Plan | ⬜ | Trade/group rooms + **SSE** stream (MVP Wave 3) |
| [moderation](./moderation_module.md) | Plan | ⬜ | `BlockUser` only |

---

## Dependency graph

```mermaid
flowchart TB
    user[user ✅]
    seller[seller_profile ✅ partial]
    portfolio[profile_portfolio ✅ partial]

    traits[traits ⬜]
    media[media ⬜]
    geo[geolocation ⬜]
    feed[feed ⬜]
    groups[groups ⬜]

    trade[trade ⬜]
    validation[validation ⬜]
    chat[chat ⬜]
    mod[moderation ⬜]

    user --> seller
    user --> portfolio
    user --> feed
    user --> trade
    user --> chat
    user --> mod
    user --> groups

    traits --> feed
    media --> feed
    geo --> feed

    feed --> seller
    feed --> portfolio
    feed --> groups

    trade --> validation
    trade --> portfolio
    trade --> chat
    trade --> seller

    groups --> chat
    validation --> user
    chat --> mod
    feed --> mod
    trade --> mod
    groups --> mod
```

---

## Recommended waves

Execute in order. Within a wave, items marked **∥** can run in parallel.

### Wave 1 — Marketplace core + communities (**MVP**)

**Goal:** Users post/browse offers/needs/trips **and** join FB-style communities with group-scoped feed. Groups are **not** post-MVP polish — they are part of the product spine ([product_vision.md](./product_vision.md)).

| Step | Module | Deliverable | Doc |
|------|--------|-------------|-----|
| 1.1 | **traits** | `traits` table, seed tree, SQLDelight, no standalone route yet | [traits_module.md](./traits_module.md) |
| 1.2 | **feed** Phase A–C | Schema, `FeedService`, `CreateFeedItem`, `ListForYouFeed`, blocks filter | [feed_module.md](./feed_module.md) |
| 1.3 | **feed** Phase D | `ListFeedByAuthor`, `ListFeedByTrait` | feed |
| 1.3b | **feed** Phase G | Social: like/bump, comments, bookmarks | feed |
| 1.4 | **groups** Phase A | `groups` / `group_members`, create/join/leave | [groups_module.md](./groups_module.md) |
| 1.5 | **groups** Phase B + **feed** | `feed_items.group_id`, `GET /feed/by-group/{id}`, member-only create | groups, feed |
| 1.6 | **seller_profile** Phase C | Wire seller listings tab; sync `seller_activity_stats` | [seller_profile_module.md](./seller_profile_module.md) |

**Exit criteria:** Seller page = profile header + author feed; public group with members + group feed posts via REST; like/bump + comment + bookmark work on feed items.

---

### Wave 2 — Rich listings (media + fingerprint location)

**Goal:** Image uploads, async tagging, trip/location signals on feed.

| Step | Module | Deliverable | Doc |
|------|--------|-------------|-----|
| 2.1 ∥ | **media** Phase A–B | Presigned upload RPC, object keys on feed/portfolio | [media_module.md](./media_module.md) |
| 2.2 ∥ | **geolocation** Phase A | Client-reported network fingerprint ingest, `location_tag` refresh | [geolocation_module.md](./geolocation_module.md) |
| 2.3 | **feed** Phase E | Async media embeddings, optional image vector blend | feed |
| 2.4 | **seller_profile** Phase D | `RequestAvatarUpload` | seller_profile |
| 2.5 | **profile_portfolio** Phase C | `feed_items.body_document_id`, list vs detail markdown | profile_portfolio |

**Exit criteria:** Create offer with photo; trip posts show coarse location tag.

---

### Wave 3 — Barter lifecycle + group chat + SSE (**MVP**)

**Goal:** Two parties coordinate a swap/meetup (with location mode) and close it with trust impact; communities get multi-party chat over **SSE**.

| Step | Module | Deliverable | Doc |
|------|--------|-------------|-----|
| 3.1 | **trade** Phase A–B | `trades` schema, state machine, templates (swap / meetup), **location decision matrix** | [trade_module.md](./trade_module.md) |
| 3.2 | **chat** Phase A–B | Trade-scoped room, message persistence, **SSE** event stream (`GET /api/events/stream`) | [chat_module.md](./chat_module.md) |
| 3.3 | **groups** Phase C + **chat** | Group-scoped multi-party rooms (`chat_rooms.group_id`) | [groups_module.md](./groups_module.md), chat |
| 3.4 | **trade** Phase C | Link feed item → trade; cancel/expire rules; **payment-ready hooks** (no rails) | trade |
| 3.5 | **validation** Phase A–B | PIN/QR generation, handoff verify, trust delta | [validation_module.md](./validation_module.md) |
| 3.6 | **user** | `RecordPeerRating` caller from validation; optional `SubmitRating` RPC | user_module |

**Exit criteria:** Alice offers → Bob starts trade (location mode set) → chat via SSE → meetup PIN → trust + rating unlock; group room with 2+ members messaging; `PaymentGateway` port stub present but unused.

---

### Wave 4 — Profile depth & public history *(post-MVP polish)*

| Step | Module | Deliverable | Doc |
|------|--------|-------------|-----|
| 4.1 | **profile_portfolio** Phase D | `ListPublicActivity` from fulfilled feed items | profile_portfolio |
| 4.2 | **trade** Phase D | `trade_public_disclosures`, opt-in summaries | trade |
| 4.3 | **profile_portfolio** Phase E | Case-study portfolio items linked to trades | profile_portfolio |
| 4.4 | **seller_profile** | `completed_trade_count` on activity stats | seller_profile |

---

### Wave 5 — Safety & scale

| Step | Module | Deliverable | Doc |
|------|--------|-------------|-----|
| 5.1 | **moderation** Phase A | `ReportUser`, report queue, auto-hide pending review | [moderation_module.md](./moderation_module.md) |
| 5.2 | **moderation** Phase B | Admin review RPCs, enforce on feed/chat/trade/groups | moderation |
| 5.3 | **feed** Phase F | `feed_item_cards` projection (if metrics justify) | feed |
| 5.4 | **profile_portfolio** Phase G | Full-text search on `documents.source` | profile_portfolio |

---

## Migration numbering (next files)

Shipped DDL: SQLDelight **`0.sqm`** (users, profiles, stats, blocks, ratings, seller_activity_stats, documents, portfolio, pins).  
**Next migration:** `1.sqm` — **combine traits + feed** for Wave 1.

| File | Wave | Module(s) |
|------|------|-----------|
| `0.sqm` | — | user + seller + portfolio (init; mutate until prod) |
| `1.sqm` / `000002_feed.sql` | 1 | traits + feed (nullable `group_id` OK before groups table if deferred FK) |
| `000003_groups.sql` | 1 | groups + `group_members`; FK `feed_items.group_id` |
| `000004_media.sql` | 2 | media (or merge into feed if tables already stubbed) |
| `000005_geolocation.sql` | 2 | geolocation |
| `000006_trades.sql` | 3 | trade |
| `000007_chat.sql` | 3 | chat (+ `chat_rooms.group_id` for groups-C) |
| `000008_validation.sql` | 3 | validation |
| `000009_moderation.sql` | 5 | moderation |

Canonical table index: [schema.md](./schema.md). Renumber before merge if plans change.

---

## Cross-cutting rules (all modules)

Single source: **[conventions.md](./conventions.md)** — thin clients, keyset pagination, markdown-only storage, test paths, proto workflow, N+1 rules.

Quick checklist:

1. **Thin clients** — validation, state machines, and SQL on backend only.
2. **Keyset pagination** — shared cursors in [api_index.md](./api_index.md).
3. **Markdown** — `documents.source` only; see [profile_portfolio_module.md](./profile_portfolio_module.md).
4. **Tests** — `features/{name}/src/test/kotlin/`, never colocated with production sources.
5. **OpenAPI** — update `core/openapi/`; regenerate web client with `gen-api` when routes change.
6. **Verify** — `./gradlew test` before marking a wave done.
7. **Docs** — update module status + [docs/README.md](./README.md) when shipping phases.

---

## Client rollout (after backend waves)

| Wave | Web | Android | iOS |
|------|-----|---------|-----|
| 1 | Feed + groups + seller listings + social | Feed + groups + social | Feed + groups + social |
| 2 | Media upload + location tag | Same | Same |
| 3 | Trade (location modes) + chat SSE + group rooms + QR | Same | Same |
| 4 | Activity + case studies | Profile tabs | Profile tabs |
| 5 | Report flow | Same | Same |

---

## Phase ID convention

Use stable IDs in PRs and checkboxes: `{module}-{phase}` (e.g. `feed-A`, `seller-C`, `trade-B`). Each module doc lists phases with these IDs.

**PR rule:** one **primary** module ID per PR. Supporting work (e.g. traits SQL inside feed-A) is noted in the PR body.

---

## Execution playbook

### 1. Ship vertical slices, not isolated modules

Optimize for user-visible outcomes:

| Wave | Vertical slice | Primary phase IDs |
|------|----------------|-------------------|
| 1 | Seller listings **+ community group feed** + social | `feed-A`…`feed-D`, `feed-G`, `traits-A`, `groups-A`, `groups-B`, `seller-C` |
| 2 | Photo offer + location tag | `media-A`, `geo-A`, `portfolio-C` |
| 3 | Complete deal (swap/meetup + location mode) **+ group chat SSE** | `trade-A`…`trade-C`, `chat-A`, `chat-B`, `groups-C`, `validation-A` |
| 4 | Public history | `portfolio-D`, `trade-D` |
| 5 | Safety | `mod-A`, `mod-B` |

### 2. Use Koin contract interfaces for cross-feature calls

See [architecture.md](./architecture.md#cross-feature-internal-api). Feed/trade/validation must not import sibling `*Service.kt` directly.

### 3. Definition of done (per phase)

A phase is **done** only when:

1. Backend tests pass (`./gradlew test`)
2. Module doc + [docs/README.md](./README.md) status updated
3. **At least web** calls new RPCs in a minimal screen *(when RPC is user-facing)*
4. Projection registry updated if denormalized tables change

### 4. Deferred / polish phases (explicitly out of MVP path)

**MVP path = Waves 1–3** (feed + **groups A–B** + media/geo + trade/chat/**groups-C** + validation). Wave 4–5 and rows below are polish.

| Phase | Module | Note |
|-------|--------|------|
| feed-F | feed | `feed_item_cards` — only if metrics justify |
| portfolio-G | profile | Full-text search on documents |
| chat-C | chat | Typing indicators |
| media-D | media | AI tag metadata display |
| payments | trade | Live Stripe/PayU/BLIK + InPost — **post-MVP**; MVP keeps `PaymentGateway` port stub only |
| groups-D | groups | Owner remove/report hooks — with moderation Wave 5 |
| groups discovery | groups | `GET /api/groups` search — link/slug enough for MVP |

### 5. Wave 1 integration test

Golden path test (skipped until feed ships): `app/src/test/kotlin/.../Wave1SellerPageTest.kt`

---

## Quick reference — all docs

| Module | Doc | Meta |
|--------|-----|------|
| Product vision | [product_vision.md](./product_vision.md) | audience, AI, safety |
| User & auth | [user_module.md](./user_module.md) | [trust_events.md](./trust_events.md) |
| Seller profile | [seller_profile_module.md](./seller_profile_module.md) | [auth_and_permissions.md](./auth_and_permissions.md) |
| Profile & portfolio | [profile_portfolio_module.md](./profile_portfolio_module.md) | [schema.md](./schema.md) |
| Feed | [feed_module.md](./feed_module.md) | social: like/bump, comments, bookmarks |
| Traits | [traits_module.md](./traits_module.md) | owned by feed migration |
| Groups | [groups_module.md](./groups_module.md) | **MVP** communities |
| Media | [media_module.md](./media_module.md) | |
| Geolocation | [geolocation_module.md](./geolocation_module.md) | fingerprints; trade owns location modes |
| Trade | [trade_module.md](./trade_module.md) | swap + meetup + location matrix + payment prep |
| Validation | [validation_module.md](./validation_module.md) | |
| Chat | [chat_module.md](./chat_module.md) | REST + **SSE** |
| Moderation | [moderation_module.md](./moderation_module.md) | |
| Clients | [clients.md](./clients.md) | [api_index.md](./api_index.md) |
| Onboarding | [README.md](./README.md) | [MODULE_TEMPLATE.md](./MODULE_TEMPLATE.md) |
