# Guide: Media Module

**MinIO/S3 uploads**, object key validation, and **async AI tagging** (vector embeddings on images/video).

**Status:** Doc complete · **Backend:** ✅ MVP (Ktor upload/download + registry GC; embeddings deferred) · **Feature:** `features:media`

**Depends on:** [feed_module.md](./feed_module.md) feed-A · **Unblocks:** feed-E, [seller_profile](./seller_profile_module.md) seller-D, portfolio covers

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 2.1 (`media-A`, `media-B`)

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#media-module-planned--wave-2) · [conventions.md](./conventions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Ktor upload/download** | Clients POST/GET bytes via `/api/media/*`; Ktor talks to MinIO |
| **Object keys** | `feed_item_media`, portfolio `cover_object_key`, avatars |
| **Media registry** | Every S3 object tracked in `media_objects` — pending → active → deleted |
| **Reference tracking** | `ref_count` on registry; commit on link, release on unlink |
| **Garbage collection** | Orphan pending uploads + unreferenced objects removed from MinIO |
| **Async embeddings** | Worker writes `feed_item_media.embedding` |
| **Validation** | Allowed bucket/prefix only on write |

---

## Architecture

```text
Client                    MediaService              MinIO
  │ POST /media/uploads ────► INSERT media_objects (pending)
  │   (raw bytes)             PUT object ───────────► object
  │                           ← objectKey + /api/media/objects?key=…
  │ UpsertPortfolio(keys[]) ─► commitKeys() → active, ref_count++
  │ GET /media/objects?key= ─► GET object ◄─────────── stream
```

**Ownership:** `features:media` owns the registry, upload/download proxy, commit/release, GC, and MinIO deletes. Feed/user features **store key strings** in their tables and call `MediaService.commitKeys` / `releaseKey` inside the same transaction.

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `MediaRouting.kt` | HTTP paths, OpenAPI metadata |
| Service | `MediaService.kt` | Presign, commit/release, key validation, GC queries |
| Integration | `MediaConsumer.kt` | Embedding jobs + optional GC trigger |
| Integration | `MediaPublisher.kt` | `media.embedding.requested` events |
| Integration | `MediaGcConsumer.kt` | Scheduled orphan + soft-delete purge |

Register in Koin (`mediaModule`) and mount routes from `Application.kt` via `configureMediaRouting()`.

---

## Media management

Every object in MinIO has a row in **`media_objects`** ([schema.md](./schema.md#media-module-planned--wave-2)). Feature tables (`feed_item_media`, `cover_object_key`, `avatar_url`) store key strings only; lifecycle and GC are centralized here.

### Lifecycle

```text
pending ──commitKeys()──► active ──releaseKey()──► deleted ──GC──► (row + S3 object removed)
   │                         │
   └── abandon / TTL 24h ────┴── replace avatar/cover / delete feed item
```

| Status | Meaning | MinIO object |
|--------|---------|--------------|
| `pending` | Presigned, not yet linked to feed/profile | exists; GC after 24h |
| `active` | Linked; `ref_count > 0` | exists |
| `deleted` | Unlinked; grace period | exists until GC |

### Key layout

All presigned uploads use:

```text
uploads/{owner_user_id}/{uuid}
```

- Issued only after `media_objects` row inserted (`status = pending`)
- `RequestUpload` rejects keys outside `uploads/{caller_user_id}/`
- On commit, key stays at same path (no server-side copy for MVP)

### Commit and release (API for other features)

Other features **must not** flip `media_objects.status` directly. Call `MediaService` in the same DB transaction as the linking write.

**Commit** — when a key is first referenced:

```kotlin
// called by FeedService / UserService inside their transaction
mediaService.commitKeys(
    keys = mediaObjectKeys,
    ownerUserId = authorId,
)
// pending → active, ref_count += 1 per key
// throws if: wrong owner, not pending, key missing
```

**Release** — when a reference is removed:

```kotlin
mediaService.releaseKey(objectKey)
// ref_count -= 1; if 0 → status = deleted, delete_after = now + 7 days
```

| Event | Caller | Action |
|-------|--------|--------|
| `CreateFeedItem` with keys | FeedService | `commitKeys` + insert `feed_item_media` |
| `UpdateFeedItem` media swap | FeedService | `commitKeys` new; `releaseKey` removed |
| `DeleteFeedItem` | FeedService | `releaseKey` for each `feed_item_media` row |
| Avatar / cover replace | UserService | `commitKeys` new; `releaseKey` old |
| Portfolio delete | UserService | `releaseKey` on `cover_object_key` if set |

### Garbage collection

`MediaGcConsumer` runs daily (JobRunr recurring job or Ktor scheduler):

1. **Abandoned uploads** — `status = pending` AND `created_at < now() - 24h` → delete MinIO object → delete row
2. **Soft-deleted** — `status = deleted` AND `ref_count = 0` AND `delete_after < now()` → delete MinIO object → delete row

```sql
-- media.sq (GC candidate queries)
selectGcPendingOrphans:
SELECT object_key FROM media_objects
WHERE status = 'pending' AND created_at < :cutoff;

selectGcDeletedReady:
SELECT object_key FROM media_objects
WHERE status = 'deleted' AND ref_count = 0 AND delete_after < :now;
```

- Batch 500 keys per run; idempotent `deleteObject`
- Log failures; retry next run (do not delete row if S3 delete fails)

### MinIO lifecycle (safety net)

Bucket lifecycle rule on `uploads/` prefix only:

- expire objects after **2 days**

Catches DB bugs. Do **not** apply lifecycle to committed paths outside `uploads/` until promotion strategy exists.

### What not to do

| Anti-pattern | Why |
|--------------|-----|
| S3-only lifecycle for all prefixes | cannot see DB refs |
| Full bucket scan vs DB | slow, races |
| Delete on presign expiry | upload may still be in flight |
| Immediate delete on replace | no rollback / CDN grace |

---

## Schema

Canonical DDL: [schema.md](./schema.md#media-module-planned--wave-2).

- **`media_objects`** — registry (Wave 2, `000003_media.sql` or merged into feed migration)
- **`feed_item_media`** — feed linkage (feed migration)
- **`user_portfolio_items.cover_object_key`**, **`user_profiles.avatar_url`** — profile linkage (init migration)

**Next migration:** `000003_media.sql` for `media_objects` if not merged into `000002_feed.sql`.

---

## REST / OpenAPI surface

| Operation | Auth |
|-----------|------|
| `RequestUpload` | Auth |
| `RequestAvatarUpload` | Auth |

[api_index.md](./api_index.md)

---

## Auth policy

All upload routes require authenticated user; object keys scoped to `user_id` prefix. [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

### Phase media-A — Upload plumbing

- [ ] `mediaModule` in Koin; mount routes via `configureMediaRouting()` in `Application.kt`
- [ ] `RequestUpload` → presigned PUT + `object_key` + expiry
- [ ] Env: `S3_*` from local/docker config → MinIO client in `core/storage`
- [ ] Tests: `features/media/src/test/kotlin/` — mock MinIO, reject bad prefix

### Phase media-B — Feed & profile integration

- [ ] `CreateFeedItem` accepts `media_object_keys[]`
- [ ] `RequestAvatarUpload` (seller-D)
- [ ] Portfolio `cover_object_key` validation

### Phase media-C — Async embeddings

- [ ] `MediaConsumer` + `vector(768)` column
- [ ] Stub embed client for CI
- [ ] Optional blend into feed ranking (feed-E)

### Phase media-D — AI tags (optional)

- [ ] Tag metadata from nearest-neighbor
- [ ] Expose on `GetFeedItem`

### Phase media-E — Registry & GC

- [ ] `media_objects` table + SQLDelight queries (`commit`, `release`, GC selects)
- [ ] `commitKeys` / `releaseKey` on `MediaService`
- [ ] Feed/user hooks: create/update/delete feed item, avatar/cover swap
- [ ] `MediaGcConsumer` daily purge
- [ ] MinIO lifecycle rule on `uploads/` prefix
- [ ] Tests: abandoned pending deleted; replaced avatar releases old key; active key never GC'd

---

## Verification

```bash
./gradlew :core:database:generateSqlDelightInterface
./gradlew :features:media:test
./gradlew test
# manual: presigned PUT → CreateFeedItem with key → object readable
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `core/openapi/src/main/kotlin/dto/` | OpenAPI DTOs |
| `features/media/src/main/kotlin/.../MediaRouting.kt` | HTTP routes |
| `features/media/src/main/kotlin/.../MediaService.kt` | Presign, commit/release, validation |
| `features/media/src/main/kotlin/.../MediaConsumer.kt` | Embedding worker |
| `features/media/src/main/kotlin/.../MediaPublisher.kt` | MQ publish |
| `features/media/src/main/kotlin/.../MediaGcConsumer.kt` | Orphan + soft-delete GC |
| `core/database/src/main/sqldelight/media.sq` | Registry + GC queries |
| `core/storage/` | MinIO client |
| `features/media/src/test/kotlin/...` | Tests |
| `docs/media_module.md` | This guide |

---

## Open questions

| Question | MVP default |
|----------|-------------|
| Separate bucket per env | Single MinIO bucket in local dev |
| Max upload size | Enforced at presign + reverse proxy |
| Video embeddings | Images first; video deferred |
| Pending TTL | 24 hours |
| Delete grace | 7 days after `ref_count` hits 0 |
| GC cadence | Daily batch |

---

## Related documentation

- [feed_module.md](./feed_module.md) Phase feed-E
- [clients.md](./clients.md) — client PUT flow
