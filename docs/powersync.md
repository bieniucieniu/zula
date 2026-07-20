# PowerSync

Local-first sync for web + native via self-hosted PowerSync Service.

## Layout

| Path | Role |
|------|------|
| [`powersync/`](../powersync/) | Service config, Sync Streams, Docker Compose |
| [`packages/powersync`](../packages/powersync/) | Shared `@zula/powersync` AppSchema + data-source helpers |

## Devenv

```bash
devenv --profile powersync up   # postgres + Ktor + PowerSync :8081
cd packages/powersync && bun run gen
```

Linux: `docker-client` from devenv packages. Mac: Docker Desktop on PATH (Nix package omitted).

## Sync Streams (profiles)

| Stream | Subscribe | Data |
|--------|-----------|------|
| `me` | auto | Own `users` + `user_profiles` |
| `user_profile` | on-demand `{ user_id }` | One public profile |

**Lazy?** On-demand streams yes — nothing until `subscribe()`.  
**Leftover 3 months?** Only if you keep subscribing or use long/infinite TTL. Default TTL after unsubscribe = **24h**, then client SQLite drops those rows. Prefer `{ ttl: 0 }` or short TTL for browse-once profiles. Never `auto_subscribe` the whole profile table.

## Auth

JWKS: Ktor `/.well-known/jwks.json`. Audience: `zula`.

## When PowerSync is down — API fallback

Clients should support modes (see `@zula/powersync` `DataSourceMode`):

| Mode | Behavior |
|------|----------|
| `api` | REST only (`API_URL` / `@zula/api`) |
| `powersync` | Local SQLite only |
| `powersync-with-api-fallback` | Prefer sync; if Service unreachable / not first-synced, fetch via API |

`resolveDataSourceConfig()` picks `api` when `POWERSYNC_URL` is unset; otherwise `powersync-with-api-fallback`. Screens that need listings before sync should always keep a REST path.

## Env

| Variable | Purpose |
|----------|---------|
| `POWERSYNC_URL` | Service origin (local `http://127.0.0.1:8081`) |
| `POWERSYNC_REPLICATION_PASSWORD` | `powersync_role` password |
| `POWERSYNC_STORAGE_USERNAME` / `PASSWORD` | Bucket DB (`zula_powersync`) |
