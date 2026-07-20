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

## Env

| Variable | Purpose |
|----------|---------|
| `PS_URL` | Service origin (local `http://127.0.0.1:8081`) |
| `PS_REPLICATION_PASSWORD` | `powersync_role` password |
| `PS_STORAGE_USERNAME` / `PS_STORAGE_PASSWORD` | Bucket DB (`zula_powersync`) |
