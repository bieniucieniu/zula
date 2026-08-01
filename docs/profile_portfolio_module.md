# Guide: Building the Profile, Portfolio & Rich Text Module From Zero

GitHub-style profiles: README, pinned work, portfolio, public activity, and app-wide markdown documents.

**Status:** Doc complete · **Backend:** ✅ A–B (`offer` feed link + activity stub open) · **Feature:** `features:user`

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
| Profile bio | `user_profile_bio` → `documents` |
| Pinned repositories | `user_profile_pins` → `user_portfolio_items` |
| Repository list | Portfolio grid + `GET /api/feed/by-author/{id}` (active listings) |
| Contribution graph | `GET /api/activity/public` (fulfilled items, public deals) |
| Stars / reputation | `user_stats` + `user_ratings` (existing) |
| Short name / headline | `seller_headline` on `user_profiles` |
| Profile bio | `user_profile_bio` → `documents` (markdown) |

**Principle:** one markdown **bio** document per user (via `documents`). Do not keep a separate plain-text bio column.

---

## Architecture Overview

```text
Client (profile page)
   │
   ├─ GET /api/sellers/{idOrUsername}
   │     ├─ header: user_profiles + user_stats
   │     ├─ readme: documents (source markdown)
   │     └─ pins[]: user_profile_pins → user_portfolio_items
   │
   ├─ GET /api/portfolio?user_id=
   ├─ GET /api/activity/public?user_id=
   ├─ GET /api/feed/by-author/{id}       -- active listings (features:feed)
   └─ GET /api/reviews/seller/{userId}

Owner editing
   ├─ PATCH /api/users/me/profile       -- short fields
   ├─ PUT /api/users/me/readme          -- documents
   ├─ POST /api/portfolio/items
   ├─ POST /api/portfolio/pins / DELETE …
   └─ PUT /api/portfolio/pins/reorder

App-wide long text
   └─ documents table referenced by:
         profile readme, feed_items.body, portfolio case studies, trade notes
```

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `features/user/.../UserRouting.kt` | Portfolio/readme/activity routes |
| Service | `features/user/.../UserService.kt` | Document helpers, pin limits, validation |
| DI | `features/user/.../UserModule.kt` | Koin wiring |

---

## Step 1: Shared Rich Text (`documents`)

Long text appears in profiles, feed posts, portfolio case studies, and trade summaries. Store it **once** in a shared table; reference by ID everywhere else.

### 1.1 Schema

Tables are in **`auth_schema.sq`** (documents planned in a future migration). Canonical reference: [schema.md](./schema.md#profile--portfolio-module).

```sql
-- excerpt — ids assigned by DB DEFAULT uuidv7()
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    format TEXT NOT NULL DEFAULT 'markdown' CHECK (format = 'markdown'),
    source TEXT NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 1,
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
- List/card UIs truncate `sourceMarkdown` locally when a short preview is needed.

### 1.4 URL validation (backend)

- Optional: reject disallowed image/link URL prefixes on write (MinIO keys, configured CDN hosts).
- Do not accept pre-rendered HTML blobs from clients — markdown only in `source`.

---

## Step 2: Profile README

### 2.1 Schema

```sql
CREATE TABLE user_profile_readme (
    user_id       UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    document_id   UUID NOT NULL REFERENCES documents(id) ON DELETE RESTRICT,
    updated_at    BIGINT NOT NULL
);
```

One readme per user. Deleting a user cascades profile row; `documents` cascade from `owner_user_id`.

### 2.2 Short bio vs README

| Field | Table | Use |
|-------|-------|-----|
| `bio` | `user_profiles` | Plain text ≤2000 chars — feed cards, search snippets |
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
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind              varchar(30) NOT NULL
                      CHECK (kind IN ('creation', 'offer', 'case_study', 'external_link')),
    title             varchar(200) NOT NULL,
    summary           text,
    body_document_id  UUID REFERENCES documents(id) ON DELETE SET NULL,
    feed_item_id      UUID REFERENCES feed_items(id) ON DELETE SET NULL,
    trade_id          UUID,  -- REFERENCES trades(id) when trade module exists
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
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    portfolio_item_id   UUID NOT NULL REFERENCES user_portfolio_items(id) ON DELETE CASCADE,
    sort_order          int NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, portfolio_item_id)
);

CREATE INDEX user_profile_pins_order_idx
    ON user_profile_pins (user_id, sort_order);
```

Enforce **max 6 pins** in `pinPortfolioItem` service logic.

### 3.3 Validation rules

| Rule | Detail |
|------|------|
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
| **Listings tab** | `GET /api/feed/by-author/{id}` | Active marketplace posts |

### 4.1 Public activity query (depends on feed module)

```sql
-- listPublicActivity: union or single query over:
--   feed_items WHERE author_id = ? AND status IN ('fulfilled', 'expired')
--   ordered by updated_at DESC, id DESC
-- Future: JOIN trade_public_disclosure for completed trades
```

Keyset pagination on `(activity_at, id)`.

### 4.2 Trade public disclosure (when trade module exists)

Never auto-publish full trade details (addresses, PINs, meetup notes).

```sql
CREATE TABLE trade_public_disclosures (
    trade_id                    UUID PRIMARY KEY,  -- REFERENCES trades(id)
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

## Step 5: REST / OpenAPI Surface

Extend DTOs in `core/openapi/src/main/kotlin/dto/` (`document.kt`, `portfolio.kt`). Regenerate with `./gradlew :core:openapi:build`. Full index: [api_index.md](./api_index.md).

### 5.1 Routes

| Method | Path | Request | Response | Auth |
|--------|------|---------|----------|------|
| `GET` | `/api/sellers/{idOrUsername}` | — | `SellerProfileResponse` (+ readme, pins) | Public |
| `GET` | `/api/users/{idOrMe}/readme` | — | `RichDocument` | Public (`me` needs JWT) |
| `PUT` | `/api/users/me/readme` | `UpdateProfileReadmeRequest` | `RichDocument` | Auth |
| `GET` | `/api/users/{idOrMe}/portfolio` | `ProfileCursor`, `limit` | `ListPortfolioItemsResponse` | Public |
| `POST` | `/api/users/me/portfolio/items` | `UpsertPortfolioItemRequest` | `PortfolioItem` | Auth |
| `DELETE` | `/api/users/me/portfolio/items/{id}` | — | `DeletePortfolioItemResponse` | Auth |
| `POST` | `/api/users/me/portfolio/pins` | `PinPortfolioItemRequest` | `PinPortfolioItemResponse` | Auth |
| `DELETE` | `/api/users/me/portfolio/pins/{id}` | — | `UnpinPortfolioItemResponse` | Auth |
| `PUT` | `/api/users/me/portfolio/pins/reorder` | `ReorderProfilePinsRequest` | `ReorderProfilePinsResponse` | Auth |
| `GET` | `/api/users/{idOrMe}/activity` | `ProfileCursor`, `limit` | `ListPublicActivityResponse` | Public (empty until feed) |

### 5.2 DTO summaries

```kotlin
// core/openapi/src/main/kotlin/dto/document.kt

@Serializable
data class RichDocument(
    val id: String,
    val format: String = "markdown",
    val sourceMarkdown: String,
    val revision: Int,
    val updatedAt: String,
)

@Serializable
data class UpdateDocumentRequest(
    val documentId: String? = null,       // null = create
    val sourceMarkdown: String,
    val expectedRevision: Int? = null,      // optimistic concurrency
)

@Serializable
data class UpdateProfileReadmeRequest(
    val sourceMarkdown: String,
    val expectedRevision: Int? = null,
)

// core/openapi/src/main/kotlin/dto/portfolio.kt

@Serializable
data class PortfolioItem(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String?,
    val body: RichDocument? = null,       // omitted on list; full on detail
    val feedItemId: String?,
    val tradeId: String?,
    val externalUrl: String?,
    val coverUrl: String?,
    val visibility: String,
    val sortOrder: Int,
    val createdAt: String,
)

@Serializable
data class SellerProfileResponse(
    // ... existing header fields (seller_profile_module.md) ...
    val readme: RichDocument? = null,
    val pins: List<PortfolioItem> = emptyList(),
)

@Serializable
data class ListPortfolioItemsResponse(
    val items: List<PortfolioItem>,
    val nextCursor: PortfolioCursor?,
    val hasMore: Boolean,
)
```

### 5.3 Feed integration — long bodies

In `features:feed`, replace plain `body` string with document reference:

```kotlin
@Serializable
data class FeedItem(
    // ...
    val bodyDocumentId: String? = null,
    val body: RichDocument? = null,      // full markdown on GET detail; omitted on list
)
```

List endpoints omit `body`; clients truncate markdown locally for cards. Detail view returns full `sourceMarkdown`.

### Auth policy

Public routes (`GET /sellers/…`, `GET /portfolio`, `GET /activity/public`, `GET /users/{id}/readme`) mount outside `authenticate("auth-jwt")` in `UserRouting.kt`. Owner mutations require JWT. Block policy A applies to all public reads — see [auth_and_permissions.md](./auth_and_permissions.md).

```kotlin
// features/user/src/main/kotlin/.../UserRouting.kt (portfolio subset)
fun Route.configurePortfolioRouting(userService: UserService) {
    route("/api") {
        get("/users/{userId}/readme") {
            call.respond(userService.getProfileReadme(call.parameters["userId"]!!, call.optionalUserId()))
        }
        get("/portfolio") { /* user_id query param */ }
        get("/activity/public") { /* user_id query param */ }

        authenticate("auth-jwt") {
            put("/users/me/readme") {
                call.respond(userService.updateProfileReadme(call.requireUserId(), call.receive()))
            }
            route("/portfolio") {
                post("/items") { /* upsert */ }
                delete("/items/{id}") { /* … */ }
                post("/pins") { /* max 6 enforced in UserService */ }
                delete("/pins/{id}") { /* … */ }
                put("/pins/reorder") { /* … */ }
            }
        }
    }
}
```

---

## Step 6: SQLDelight Queries

Add `core/database/src/main/sqldelight/documents.sq` and `profile_portfolio.sq`. Regenerate with `./gradlew :core:database:generateSqlDelightInterface`.

### 6.1 Documents

```sql
createDocument:
INSERT INTO documents (owner_user_id, format, source)
VALUES (?, ?, ?)
RETURNING *;

-- expectedRevision: pass null to skip optimistic check; otherwise WHERE revision = ?
updateDocument:
UPDATE documents
SET source = ?,
    revision = revision + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = ? AND owner_user_id = ?
  AND (? IS NULL OR revision = ?)
RETURNING *;

getDocument:
SELECT * FROM documents WHERE id = ? LIMIT 1;
```

### 6.2 Profile readme

```sql
getProfileReadmeDocument:
SELECT d.*
FROM user_profile_readme upr
JOIN documents d ON d.id = upr.document_id
WHERE upr.user_id = ?;

-- upsertProfileReadme: run in UserService transaction —
-- createDocument if needed, then INSERT/UPDATE user_profile_readme
```

### 6.3 Portfolio & pins

```sql
listPortfolioItemsPage:
SELECT *
FROM user_portfolio_items
WHERE user_id = ?
  AND visibility = 'public'
  AND (sort_order, id) < (?, ?)
ORDER BY sort_order DESC, id DESC
LIMIT ?;

listProfilePins:
SELECT pi.*, p.*
FROM user_profile_pins pin
JOIN user_portfolio_items pi ON pi.id = pin.portfolio_item_id
WHERE pin.user_id = ?
ORDER BY pin.sort_order
LIMIT 6;

countProfilePins:
SELECT count(*) FROM user_profile_pins WHERE user_id = ?;
```

---

## Step 7: Kotlin Service (`features/user/.../UserService.kt`)

Document helpers live in `UserService` (or a private `DocumentRepository` injected via Koin). No separate `DocumentService` for MVP.

### 7.1 `updateProfileReadme`

```kotlin
suspend fun updateProfileReadme(ownerId: Long, req: UpdateProfileReadmeRequest): RichDocument {
    validateMarkdownLength(req.sourceMarkdown, maxBytes = 32 * 1024)
    // upsert documents.source + user_profile_readme in transaction
    return queries.transactionWithResult { /* createDocument + upsertProfileReadme */ }
}
```

### 7.2 `getSellerProfile` composition

```text
Q1  header JOIN (users, user_profiles, user_stats)
Q2  readme document (JOIN user_profile_readme)
Q3  pins (listProfilePins, LIMIT 6)
```

**3 queries** for overview. Tabs load separately:

- `GET /api/portfolio` — keyset
- `GET /api/activity/public` — keyset
- `GET /api/feed/by-author/{id}` — FeedService
- `GET /api/reviews/seller/{userId}` — UserService

### 7.3 Portfolio flows

**"I'm proud of this deal"**

1. Trade completes → prompt for `trade_public_disclosures` opt-in.
2. User taps "Add to portfolio" → `POST /api/portfolio/items` kind `case_study`, link `tradeId`, pre-fill markdown template document.
3. Optional `POST /api/portfolio/pins`.

**Pin an active offer**

1. `POST /api/portfolio/items` kind `offer`, `feedItemId` set.
2. `POST /api/portfolio/pins` if desired.

### 7.4 N+1 checklist

| Risk | Mitigation |
|------|------------|
| Load full `body` document per portfolio card in grid | List returns `summary` + `coverUrl` only; document on detail |
| Render markdown per row in feed | List omits `body`; client truncates markdown locally for cards |
| Fetch reviewer profiles per review | JOIN in seller reviews query (seller profile plan) |
| Pin query per portfolio item | Single `listProfilePins` JOIN |

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

- [x] `documents` + `document_revisions` in `0.sqm`
- [x] Document helpers in `UserService`
- [x] `user_profile_readme` + `PUT /api/users/me/readme` / `GET /api/users/{idOrMe}/readme`
- [x] `GET /api/sellers/{idOrUsername}` includes readme + pins
- [x] Tests: length limits, validation

### Phase portfolio-B — Portfolio & pins ✅

- [x] `user_portfolio_items` + `user_profile_pins` in `0.sqm`
- [x] Portfolio CRUD + pin/unpin/reorder (max 6) under `/api/users/me/portfolio/**`
- [x] `GET /api/users/{idOrMe}/portfolio` keyset pagination (`ProfileCursor`)
- [ ] Link `offer` items to `feed_items` (needs feed; creates rejected until then)
### Phase portfolio-C — Feed body migration

- [ ] `feed_items.body_document_id` migration
- [ ] `POST /api/feed/items` creates document for body
- [ ] List feed omits body; detail returns `RichDocument.sourceMarkdown`
- [ ] Depends on [feed module](./feed_module.md) Phase feed-B+

### Phase portfolio-D — Public activity

- [ ] `GET /api/activity/public` from fulfilled/expired `feed_items`
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
./gradlew :core:database:generateSqlDelightInterface
./gradlew :core:openapi:build
./gradlew :features:user:test
./gradlew test

./gradlew test
db-seed
```

### Manual checks

- `GET /api/sellers/{id}` returns readme markdown + up to 6 pins
- `PUT /api/users/me/readme` persists markdown source
- Portfolio grid paginates without loading full documents
- `GET /api/feed` omits full body; detail view returns markdown
- Pin 7th item → rejected
- `offer` portfolio item references another user's listing → rejected

---

## Step 13: File Checklist

| Path | Purpose |
|------|---------|
| `core/database/src/main/resources/db/migration/000001_init.sql` | Full schema including documents, portfolio |
| `core/database/src/main/sqldelight/documents.sq` | Document SQLDelight |
| `core/database/src/main/sqldelight/profile_portfolio.sq` | Portfolio SQLDelight |
| `core/openapi/src/main/kotlin/dto/document.kt` | `RichDocument` DTOs |
| `core/openapi/src/main/kotlin/dto/portfolio.kt` | Portfolio DTOs |
| `core/openapi/src/main/resources/openapi.yaml` | Generated / merged spec |
| `features/user/src/main/kotlin/.../UserRouting.kt` | Portfolio/readme/activity routes |
| `features/user/src/main/kotlin/.../UserService.kt` | Document + portfolio handlers |
| `features/user/src/main/kotlin/.../UserModule.kt` | Koin wiring |
| `features/user/src/test/kotlin/.../PortfolioTest.kt` | Portfolio tests |
| `docs/profile_portfolio_module.md` | This guide |

---

## Design Decisions (locked)

1. **README ≠ bio** — short `bio` for cards; markdown readme for profile page.
2. **Portfolio is curated** — activity timeline is automatic; pins are manual.
3. **`documents` table** — single rich-text system for the whole app.
4. **Markdown only in `source`** — backend never stores HTML or derived preview text.
5. **Clients parse markdown** — web → HTML; iOS/Android → native node trees.
6. **Public deals are opt-in summaries** — never auto-publish sensitive trade fields.
7. **Composable REST calls** — overview ≠ full portfolio ≠ listings ≠ activity.
8. **List endpoints omit long bodies** — full `sourceMarkdown` on detail/readme endpoints only.

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
- [feed_module.md](./feed_module.md) — `feed_items`, author feed
- [AGENTS.md](../AGENTS.md) — keyset pagination, thin-client rules
