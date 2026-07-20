# PowerSync

Local-first sync for web + native via self-hosted PowerSync Service.

## Layout

| Path | Role |
|------|------|
| [`powersync/`](../powersync/) | Sync Streams + local service + compose |
| [`packages/powersync`](../packages/powersync/) | Shared `@zula/powersync` AppSchema |
| [`devenv.nix`](../devenv.nix) `services.postgres` | `zula` + `zula_powersync`, `wal_level=logical`, replication `initialSQL` |

## Devenv

```bash
devenv --profile powersync up   # postgres + Ktor + powersync compose :8081
bun run gen:powersync
```

Linux: `docker-client` from devenv packages. Mac: Docker Desktop on PATH (Nix package omitted).

Local `powersync/service.yaml` uses `!env PS_*`; compose builds URIs from secretspec. Deploy/k8s = separate config.

`devenv enterTest` asserts secretspec defaults (`PS_URL`, `PS_REPLICATION_PASSWORD`, `PS_STORAGE_USERNAME`, `PS_STORAGE_PASSWORD`) match compose `:-` fallbacks.

## Sync Streams (profiles)

| Stream | Subscribe | Data |
|--------|-----------|------|
| `me` | auto | Own `users` + `user_profiles` |
| `user_profile` | on-demand `{ user_id }` | One public profile |

**Lazy?** On-demand streams yes — nothing until `subscribe()`.  
**Leftover 3 months?** Only if you keep subscribing or use long/infinite TTL. Default TTL after unsubscribe = **24h**, then client SQLite drops those rows. Prefer `{ ttl: 0 }` or short TTL for browse-once profiles. Never `auto_subscribe` the whole profile table.

## Auth

JWKS: Ktor `/.well-known/jwks.json`. Audience: `zula`.

## Upload (TanStack / PowerSync `uploadData`)

```http
POST /api/sync/batch
Authorization: Bearer <access JWT>
```

```json
{
  "ops": [
    {
      "clientId": 1,
      "op": "PATCH",
      "table": "user_profiles",
      "id": "<user uuid>",
      "opData": { "display_name": "Ada", "bio": "…" }
    }
  ]
}
```

Server returns `200` + per-op results. Permanent rejects (`ok: false`) still complete the batch — do not block the upload queue. Retryable failures use whole-request `5xx` (StatusPages Problem Details).

Failed ops embed RFC 9457 Problem Details (optional `errors` map = field → [`ProblemErrorCode`](../packages/api/src/problemDetails.ts) string enums):

```json
{
  "results": [
    {
      "clientId": 1,
      "table": "user_profiles",
      "id": "<uuid>",
      "op": "PATCH",
      "ok": false,
      "problem": {
        "type": "about:blank",
        "title": "Bad Request",
        "status": 400,
        "detail": "Validation failed",
        "instance": "/api/sync/batch#op/1:user_profiles/<uuid>",
        "errors": {
          "display_name": ["required", "too_short"]
        }
      }
    }
  ]
}
```

Wire codes: `required`, `too_short`, `too_long`, `invalid`, `format`, `mismatch`, `taken`, `not_found`, `expired`, `forbidden`, `unauthorized`, `conflict`.

| table | ops | Handler |
|-------|-----|---------|
| `user_profiles` | PUT, PATCH | `UserProfileWriter` (own profile only; `id` = actor) |
| `users` | — | rejected |
| other | — | rejected |

Client sketch:

```ts
async uploadData(db) {
  const batch = await db.getCrudBatch(100)
  if (!batch) return
  await fetch(`${API_URL}/sync/batch`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ops: batch.crud }),
  })
  await batch.complete()
}
```

## Env

| Variable | Purpose |
|----------|---------|
| `PS_URL` | Service origin (local `http://127.0.0.1:8081`) |
| `PS_REPLICATION_PASSWORD` | `powersync_role` password |
| `PS_STORAGE_USERNAME` / `PS_STORAGE_PASSWORD` | Bucket DB (`zula_powersync`) |
