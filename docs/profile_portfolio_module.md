# Guide: Building the Profile, Portfolio & Rich Text Module From Zero

GitHub-style profiles: README, pinned work, portfolio, public activity, and app-wide markdown documents.

**Status:** Doc complete · **Backend:** 🔶 partial (portfolio-A, portfolio-B) · **Service:** `UserService`

**Depends on:** [user_module.md](./user_module.md), [seller_profile_module.md](./seller_profile_module.md) · **Unblocks:** rich feed bodies (portfolio-C)

**Master plan:** [implementation_plan.md](./implementation_plan.md) Waves 2.5, 4.1, 4.3

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#profile--portfolio-module) · [conventions.md](./conventions.md) · [feed_module.md](./feed_module.md) · [trade_module.md](./trade_module.md)

In Zula there is no separate seller account: any user can buy, sell, or barter. This module defines how a **public profile page** is structured, how long-form text is stored once and reused everywhere, and how it connects to the feed and trade modules.

---

## Goals

| Goal | Detail |
|------|--------|
| **Profile README** | Markdown (or rich document) block on the profile — like GitHub's profile README |
| **Pinned items** | Up to 6 highlighted portfolio entries above the fold |
| **Portfolio** | Curated public work the user is proud of (not every post) |
| **Public activity** | Chronological history of fulfilled posts and completed deals (opt-in summaries) |
| **Shared rich text** | One `documents` model for readme, listing bodies, case studies, notes |
| **Thin clients** | Length validation on backend; markdown parsing/rendering on each client |
| **Composable API** | Profile header + readme + pins + tabbed lists — not one mega-endpoint |

---

## GitHub → Zula mapping

| GitHub | Zula |
|--------|------|
| Profile README | `user_profile_readme` → `documents` |
| Pinned repositories | `user_profile_pins` → `user_portfolio_items` |
| Repository list | Portfolio grid + `ListFeedByAuthor` (active listings) |
| Contribution graph | `ListPublicActivity` (fulfilled items, public deals) |
| Stars / reputation | `user_stats` + `user_ratings` (existing) |
| Short name / bio line | `seller_headline` + short `bio` on `user_profiles` |

**Principle:** keep **identity**, **readme**, **pins**, **portfolio**, and **activity timeline** as separate concepts. Do not overload `user_profiles.bio` with a full README.

---

## Architecture Overview

```text
Client (profile page)
   │
   ├─ GetSellerProfile
   │     ├─ header: user_profiles + user_stats
   │     ├─ readme: documents (source markdown)
   │     └─ pins[]: user_profile_pins → user_portfolio_items
   │
   ├─ ListPortfolioItems(user_id, cursor)
   ├─ ListPublicActivity(user_id, cursor)     -- auto history
   ├─ ListFeedByAuthor(user_id, cursor)       -- active listings (FeedService)
   └─ ListSellerReviews(user_id, cursor)

Owner editing
   ├─ UpdateMyProfile          -- short fields
   ├─ UpdateProfileReadme      -- documents
   ├─ UpsertPortfolioItem
   ├─ PinPortfolioItem / UnpinPortfolioItem
   └─ ReorderPortfolioPins

App-wide long text
   └─ documents table referenced by:
         profile readme, feed_items.body, portfolio case studies, trade notes
```

---

## Step 1: Shared Rich Text (`documents`)

Long text appears in profiles, feed posts, portfolio case studies, and trade summaries. Store it **once** in a shared table; reference by ID everywhere else.

### 1.1 Schema

Tables are in **`000001_init.up.sql`**. Canonical reference: [schema.md](./schema.md#profile--portfolio-module).

```sql
-- excerpt — see migration for full DDL
CREATE TABLE documents (
    id bigint PRIMARY KEY,
    owner_user_id bigint NOT NULL REFERENCES users(id),
    format varchar(30) NOT NULL DEFAULT 'markdown' CHECK (format = 'markdown'),
    source text NOT NULL DEFAULT '',
    revision int NOT NULL DEFAULT 1,
    ...
);
```

### 1.2 Storage model

| Layer | Responsibility |
|-------|----------------|
| **Backend** | Store canonical markdown in `documents.source`; validate length; bump `revision` |
| **Web client** | Parse markdown → HTML for display |
| **Mobile clients** | Parse markdown → native rich-text node tree (Compose / SwiftUI) |

**On save (backend):**

1. Validate length.
2. Increment `revision`; append `document_revisions` if audit enabled.

### 1.3 Client parsing

- Backend never converts markdown to HTML or any other presentation format.
- Each client owns its parser, preview, and embed resolution (`listing_embed`, `@user`, images).
- List/card UIs truncate `source_markdown` locally when a short preview is needed.

### 1.4 URL validation (backend)

- Optional: reject disallowed image/link URL prefixes on write (MinIO keys, configured CDN hosts).
- Do not accept pre-rendered HTML blobs from clients — markdown only in `source`.

---

## Step 2: Profile README

### 2.1 Schema

```sql
CREATE TABLE user_profile_readme (
    user_id       bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    document_id   bigint NOT NULL REFERENCES documents(id) ON DELETE RESTRICT,
    updated_at    timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

One readme per user. Deleting a user cascades profile row; `documents` cascade from `owner_user_id`.

### 2.2 Short bio vs README

| Field | Table | Use |
|-------|-------|-----|
| `bio` | `user_profiles` | Plain text ~300 chars — feed cards, search snippets |
| `seller_headline` | `user_profiles` | One line under name |
| README | `user_profile_readme` → `documents` | Full profile page body — markdown, images, lists |

Extend `user_profiles` if not already present:

```sql
ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS seller_headline varchar(160),
    ADD COLUMN IF NOT EXISTS location_tag varchar(100);
```

---

## Step 3: Portfolio & Pins

Portfolio is **curated** work. It is not the same as the full post history or activity timeline.

### 3.1 Portfolio items

```sql
CREATE TABLE user_portfolio_items (
    id                bigint PRIMARY KEY DEFAULT public.generate_snowflake_id(1),
    user_id           bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind              varchar(30) NOT NULL
                      CHECK (kind IN ('creation', 'offer', 'case_study', 'external_link')),
    title             varchar(200) NOT NULL,
    summary           text,
    body_document_id  bigint REFERENCES documents(id) ON DELETE SET NULL,
    feed_item_id      bigint REFERENCES feed_items(id) ON DELETE SET NULL,
    trade_id          bigint,  -- REFERENCES trades(id) when trade module exists
    external_url      text,
    cover_object_key  text,
    sort_order        int NOT NULL DEFAULT 0,
    visibility        varchar(20) NOT NULL DEFAULT 'public'
                      CHECK (visibility IN ('public', 'unlisted')),
    created_at        timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX user_portfolio_items_user_idx
    ON user_portfolio_items (user_id, sort_order, id DESC)
    WHERE visibility = 'public';
```

| `kind` | Meaning |
|--------|---------|
| `creation` | Something they made — photos + story, not necessarily for sale |
| `offer` | Pin to an active `feed_item` listing |
| `case_study` | Write-up of a deal or project they're proud of |
| `external_link` | Off-platform portfolio (validated HTTPS URL) |

### 3.2 Pins (max 6, GitHub-style)

```sql
CREATE TABLE user_profile_pins (
    user_id             bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    portfolio_item_id   bigint NOT NULL REFERENCES user_portfolio_items(id) ON DELETE CASCADE,
    sort_order          int NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, portfolio_item_id)
);

CREATE INDEX user_profile_pins_order_idx
    ON user_profile_pins (user_id, sort_order);
```

Enforce **max 6 pins** in `PinPortfolioItem` service logic.

### 3.3 Validation rules

| Rule | Detail |
|------|--------|
| `offer` kind | `feed_item_id` required; must belong to same `user_id` |
| `case_study` | `body_document_id` or `summary` required |
| `external_link` | `external_url` required; https only |
| `creation` | `cover_object_key` or `body_document_id` recommended |
| Pin | Item must be `visibility = public` |

When a pinned `offer` is fulfilled or removed, either show a badge or auto-unpin (configurable; default: show "Fulfilled" badge, keep pin).

---

## Step 4: Public Activity & Deal History

Automatic timeline vs manual portfolio:

| Surface | Source | UX |
|---------|--------|-----|
| **Portfolio tab** | `user_portfolio_items` | Curated grid |
| **Pins** | `user_profile_pins` | Top of overview |
| **Activity tab** | `feed_items` + `trades` | Auto-generated public history |
| **Listings tab** | `FeedService.ListFeedByAuthor` | Active marketplace posts |

### 4.1 Public activity query (depends on feed module)

```sql
-- name: ListPublicActivity :many
-- Union or single query over:
--   feed_items WHERE author_id = $1 AND status IN ('fulfilled', 'expired')
--   ordered by updated_at DESC, id DESC
-- Future: JOIN trade_public_disclosure for completed trades
```

Keyset pagination on `(activity_at, id)`.

### 4.2 Trade public disclosure (when trade module exists)

Never auto-publish full trade details (addresses, PINs, meetup notes).

```sql
CREATE TABLE trade_public_disclosures (
    trade_id                    bigint PRIMARY KEY,  -- REFERENCES trades(id)
    show_on_initiator_profile   boolean NOT NULL DEFAULT false,
    show_on_recipient_profile   boolean NOT NULL DEFAULT false,
    public_title                varchar(200),
    public_summary              text,
    created_at                  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Both parties opt in. `public_summary` may reference a `documents` row for markdown.

**Example public line:** "Completed barter: handmade leather bag ↔ bike tune-up"

---

## Step 5: Protocol Buffers

Extend `apps/backend/internal/apitypes/` and add `apps/backend/internal/apitypes/`.

### 5.1 Document messages

```protobuf
syntax = "proto3";
package zula;

message RichDocument {
    string id = 1;
    string format = 2;           // always "markdown" in MVP
    string source_markdown = 3;  // canonical storage
    int32 revision = 6;
    string updated_at = 7;
}

message UpdateDocumentRequest {
    string document_id = 1;      // empty = create
    string source_markdown = 2;
    optional int32 expected_revision = 3;  // optimistic concurrency
}
```

### 5.2 Profile & portfolio RPCs

```protobuf
service UserService {
    // Profile (extend seller profile plan)
    rpc GetSellerProfile(GetSellerProfileRequest) returns (SellerProfileResponse);
    rpc UpdateProfileReadme(UpdateProfileReadmeRequest) returns (RichDocument);
    rpc GetProfileReadme(GetProfileReadmeRequest) returns (RichDocument);

    // Portfolio
    rpc ListPortfolioItems(ListPortfolioItemsRequest) returns (ListPortfolioItemsResponse);
    rpc UpsertPortfolioItem(UpsertPortfolioItemRequest) returns (PortfolioItem);
    rpc DeletePortfolioItem(DeletePortfolioItemRequest) returns (DeletePortfolioItemResponse);
    rpc PinPortfolioItem(PinPortfolioItemRequest) returns (PinPortfolioItemResponse);
    rpc UnpinPortfolioItem(UnpinPortfolioItemRequest) returns (UnpinPortfolioItemResponse);
    rpc ReorderProfilePins(ReorderProfilePinsRequest) returns (ReorderProfilePinsResponse);

    // Activity
    rpc ListPublicActivity(ListPublicActivityRequest) returns (ListPublicActivityResponse);
}

message SellerProfileResponse {
    // ... existing header fields ...
    RichDocument readme = 20;
    repeated PortfolioItem pins = 21;
}

message PortfolioItem {
    string id = 1;
    string kind = 2;
    string title = 3;
    string summary = 4;
    RichDocument body = 5;
    string feed_item_id = 6;
    string trade_id = 7;
    string external_url = 8;
    string cover_url = 9;
    string visibility = 10;
    int32 sort_order = 11;
    string created_at = 12;
}

message ListPortfolioItemsRequest {
    string user_id = 1;
    PortfolioCursor cursor = 2;  // document
    int32 limit = 3;
}
```

### 5.3 Feed integration — long bodies

In `feed`, replace plain `body` string with document reference:

```protobuf
message FeedItem {
    // ...
    string body_document_id = 11;
    RichDocument body = 12;      // full markdown on GetFeedItem; omitted on list pages
}
```

List endpoints omit `body`; clients truncate markdown locally for cards. Detail view returns full `source_markdown`.

---

## Step 6: SQLC Queries

Add `apps/backend/db/query/documents.sql` and `profile_portfolio.sql`.

### 6.1 Documents

```sql
-- name: CreateDocument :one
INSERT INTO documents (owner_user_id, format, source)
VALUES ($1, $2, $3)
RETURNING *;

-- name: UpdateDocument :one
UPDATE documents
SET source = $3,
    revision = revision + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = $1 AND owner_user_id = $2
  AND (sqlc.narg(expected_revision)::int IS NULL OR revision = sqlc.narg(expected_revision))
RETURNING *;

-- name: GetDocument :one
SELECT * FROM documents WHERE id = $1 LIMIT 1;
```

### 6.2 Profile readme

```sql
-- name: GetProfileReadmeDocument :one
SELECT d.*
FROM user_profile_readme upr
JOIN documents d ON d.id = upr.document_id
WHERE upr.user_id = $1;

-- name: UpsertProfileReadme :one
-- tx: create document if needed, upsert user_profile_readme
```

### 6.3 Portfolio & pins

```sql
-- name: ListPortfolioItems :many
-- keyset on (sort_order, id)

-- name: ListProfilePins :many
SELECT pi.*, p.*
FROM user_profile_pins pin
JOIN user_portfolio_items pi ON pi.id = pin.portfolio_item_id
WHERE pin.user_id = $1
ORDER BY pin.sort_order
LIMIT 6;

-- name: CountProfilePins :one
SELECT count(*)::int FROM user_profile_pins WHERE user_id = $1;
```

---

## Step 7: Go Services

### 7.1 `DocumentService` or helpers in `UserService`

```go
func (s *UserService) UpdateProfileReadme(ctx context.Context, req *pb.UpdateProfileReadmeRequest) (*pb.RichDocument, error) {
    ownerID := requireViewer(ctx)
    // validate markdown length
    // upsert documents.source + user_profile_readme in tx
}
```

### 7.2 `GetSellerProfile` composition

```text
Q1  header JOIN (users, user_profiles, user_stats)
Q2  readme document (JOIN user_profile_readme)
Q3  pins (ListProfilePins, LIMIT 6)
```

**3 queries** for overview. Tabs load separately:

- `ListPortfolioItems` — keyset
- `ListPublicActivity` — keyset
- `ListFeedByAuthor` — FeedService
- `ListSellerReviews` — UserService

### 7.3 Portfolio flows

**"I'm proud of this deal"**

1. Trade completes → prompt for `trade_public_disclosures` opt-in.
2. User taps "Add to portfolio" → `UpsertPortfolioItem` kind `case_study`, link `trade_id`, pre-fill markdown template document.
3. Optional `PinPortfolioItem`.

**Pin an active offer**

1. `UpsertPortfolioItem` kind `offer`, `feed_item_id` set.
2. `PinPortfolioItem` if desired.

### 7.4 N+1 checklist

| Risk | Mitigation |
|------|------------|
| Load full `body` document per portfolio card in grid | List returns `summary` + `cover_url` only; document on `GetPortfolioItem` |
| Render markdown per row in feed | List omits `body`; client truncates markdown locally for cards |
| Fetch reviewer profiles per review | JOIN in `ListSellerReviews` (seller profile plan) |
| Pin query per portfolio item | Single `ListProfilePins` JOIN |

---

## Step 8: Profile Page Layout (client)

Clients compose sections; backend does not return one giant blob.

```text
┌─────────────────────────────────────────────────────────┐
│ [Avatar]  Display Name                                  │
│           @username · location · member since           │
│           ★ rating · Trust score                        │
│           seller_headline                                 │
├─────────────────────────────────────────────────────────┤
│ README (markdown → HTML / native nodes on client)       │
├─────────────────────────────────────────────────────────┤
│ Pinned (≤6)                                             │
│  [Creation] [Offer] [Case study] ...                    │
├─────────────────────────────────────────────────────────┤
│ Overview | Portfolio | Listings | Activity | Reviews    │
└─────────────────────────────────────────────────────────┘
```

---

## Step 9: App-Wide Rich Text Rollout

Use `documents` for every long text field:

| Entity | Migration |
|--------|-----------|
| Profile readme | `user_profile_readme.document_id` |
| Feed item body | `feed_items.body_document_id` |
| Portfolio case study | `user_portfolio_items.body_document_id` |
| Trade public summary | `trade_public_disclosures` + optional `document_id` |
| User bio (optional later) | Keep plain text short; do not move to markdown |

**Single pipeline:** `Validate length → store markdown in source → reference by ID`.

### Length limits (suggested)

| Field | Max |
|-------|-----|
| `bio` | 300 chars plain |
| Profile readme | 32 KB markdown |
| Feed item body | 16 KB markdown |
| Portfolio case study | 32 KB markdown |
| Review comment | 2 KB plain (existing) |

---

## Step 10: Caching

| Data | Strategy |
|------|----------|
| `documents.source` | Canonical markdown; no derived columns on backend |
| Profile header | No extra cache; JOIN `user_stats` (read cache only) |
| Pins | 6 rows max; query live |
| Full-text search | Add `tsvector` on `documents.source` in Phase G (optional) |

---

## Step 11: Implementation Phases

### Phase portfolio-A — Documents + profile README ✅

- [x] `documents` + `document_revisions` in `000001_init.up.sql`
- [x] `document` + helpers
- [x] `user_profile_readme` + `UpdateProfileReadme` / `GetProfileReadme`
- [x] `GetSellerProfile` includes readme + pins
- [x] Tests: length limits, source persistence

### Phase portfolio-B — Portfolio & pins ✅

- [x] `user_portfolio_items` + `user_profile_pins`
- [x] Portfolio CRUD + pin/unpin/reorder (max 6)
- [x] `ListPortfolioItems` keyset pagination
- [ ] Link `offer` items to `feed_items` (needs feed)

### Phase portfolio-C — Feed body migration

- [ ] `feed_items.body_document_id` migration
- [ ] `CreateFeedItem` creates document for body
- [ ] List feed omits body; detail returns `RichDocument.source_markdown`
- [ ] Depends on [feed module](./feed_module.md) Phase feed-B+

### Phase portfolio-D — Public activity

- [ ] `ListPublicActivity` from fulfilled/expired `feed_items`
- [ ] Depends on feed module

### Phase portfolio-E — Trades & case studies

- [ ] `trade_public_disclosures` when trade module ships
- [ ] `case_study` portfolio items linked to trades

### Phase portfolio-F — Structured editor (optional, client-side)

- [ ] In-app editors produce markdown before save

### Phase portfolio-G — Search

- [ ] `tsvector` on `documents.source` (optional)

---

## Step 12: Verification

```bash
# devenv shell
gen-openapi
cd apps/backend && sqlc generate
devenv test
db-seed
cd apps/backend && go test ./...
```

### Manual checks

- `GetSellerProfile` returns readme markdown + up to 6 pins
- `UpdateProfileReadme` persists markdown source
- Portfolio grid paginates without loading full documents
- `ListFeed` omits full body; detail view returns markdown
- Pin 7th item → rejected
- `offer` portfolio item references another user's listing → rejected

---

## Step 13: File Checklist

| Path | Purpose |
|------|---------|
| `apps/backend/db/migration/000001_init.up.sql` | Full schema including documents, portfolio |
| `apps/backend/db/query/documents.sql` | Document SQLC |
| `apps/backend/db/query/profile_portfolio.sql` | Portfolio SQLC |
| `apps/backend/internal/apitypes/` | Rich document messages |
| `apps/backend/internal/apitypes/` | Profile + portfolio RPCs |
| `apps/backend/internal/service/user_document.go` | Document helpers |
| `apps/backend/internal/service/user.go` | Profile + portfolio handlers |
| `docs/profile_portfolio_module.md` | This guide |

---

## Design Decisions (locked)

1. **README ≠ bio** — short `bio` for cards; markdown readme for profile page.
2. **Portfolio is curated** — activity timeline is automatic; pins are manual.
3. **`documents` table** — single rich-text system for the whole app.
4. **Markdown only in `source`** — backend never stores HTML or derived preview text.
5. **Clients parse markdown** — web → HTML; iOS/Android → native node trees.
6. **Public deals are opt-in summaries** — never auto-publish sensitive trade fields.
7. **Composable RPCs** — overview ≠ full portfolio ≠ listings ≠ activity.
8. **List endpoints omit long bodies** — full `source_markdown` on detail/readme endpoints only.

---

## Open Questions

| Question | MVP default |
|----------|-------------|
| Auto-unpin fulfilled offers? | No — show "Fulfilled" badge |
| Client-side XSS when rendering markdown? | Each client sanitizes its own output format |
| External link previews | Title + URL only; no oEmbed in MVP |
| Document ownership transfer | Not supported; documents die with owner |

---

## Related Documentation

- [user_module.md](./user_module.md) — auth, `user_profiles`, `user_stats`
- [feed_module.md](./feed_module.md) — `feed_items`, `ListFeedByAuthor`
- [AGENTS.md](../AGENTS.md) — keyset pagination, thin-client rules
