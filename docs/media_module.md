# Guide: Media Module

**MinIO/S3 uploads**, object key validation, and **async AI tagging** (vector embeddings on images/video).

**Status:** Doc complete · **Backend:** ⬜ · **Feature:** `features:media`

**Depends on:** [feed_module.md](./feed_module.md) feed-A · **Unblocks:** feed-E, [seller_profile](./seller_profile_module.md) seller-D, portfolio covers

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 2.1 (`media-A`, `media-B`)

**Related:** [architecture.md](./architecture.md) · [schema.md](./schema.md#media-module-planned--wave-2) · [conventions.md](./conventions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Presigned uploads** | Clients PUT bytes to MinIO; backend never streams bodies over REST |
| **Object keys** | `feed_item_media`, portfolio `cover_object_key`, avatars |
| **Async embeddings** | Worker writes `feed_item_media.embedding` |
| **Validation** | Allowed bucket/prefix only on write |

---

## Architecture

```text
Client                    MediaService              MinIO
  │ RequestUpload ──────────► presigned PUT URL
  │ PUT bytes ─────────────────────────────────────► object
  │ CreateFeedItem(keys[]) ──► FeedService validates keys
  │
  └── async worker ◄──────── embedding job ◄── feed_item_media row
```

**Ownership:** MediaService issues presigned URLs and validates keys. FeedService/UserService **store** key strings only. Avatar upload route may live on MediaService with UserService updating `avatar_url` after validation.

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `MediaRouting.kt` | HTTP paths, OpenAPI metadata |
| Service | `MediaService.kt` | Presign, key validation, embedding coordination |
| Integration | `MediaConsumer.kt` / `MediaPublisher.kt` | Async embedding jobs via RabbitMQ |

Register in Koin (`mediaModule`) and mount routes from `Application.kt` via `configureMediaRouting()`.

---

## Schema

Uses `feed_item_media` (feed migration) and existing `cover_object_key` / `avatar_url` columns. See [schema.md](./schema.md#media-module-planned--wave-2).

**Next migration:** `000003_media.sql` only if tables not merged into `000002_feed.sql`.

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
| `features/media/src/main/kotlin/.../MediaService.kt` | Presign + validation |
| `features/media/src/main/kotlin/.../MediaConsumer.kt` | Embedding worker |
| `features/media/src/main/kotlin/.../MediaPublisher.kt` | MQ publish (if any) |
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

---

## Related documentation

- [feed_module.md](./feed_module.md) Phase feed-E
- [clients.md](./clients.md) — client PUT flow
