# PowerSync (self-hosted)

Service config lives here. Client schema / helpers: `packages/powersync` (`@zula/powersync`).

## Layout

| File | Role |
|------|------|
| `docker-compose.yaml` | PowerSync Service on host `:8081` |
| `service.yaml` | Local/dev replication, storage, JWKS (hardcoded). Deploy = separate config. |
| `sync-config.yaml` | Sync Streams (source of truth) |

Postgres DBs (devenv): `zula` (source), `zula_powersync` (bucket storage).

Replication role + `powersync` publication: `services.postgres.initialDatabases.*.initialSQL` in [`devenv.nix`](../devenv.nix) (first Postgres init only).

## Fresh Postgres note

`wal_level=logical` + second DB only apply on **first** Postgres init. If `.devenv/state/postgres` already exists without them:

```bash
devenv down
rm -rf .devenv/state/postgres
devenv --profile powersync up
```

## Run

```bash
# Postgres + Ktor + PowerSync
devenv --profile powersync up

# Schema gen (PowerSync must be up)
cd packages/powersync && bun run gen
```

## Sync design (profiles)

| Stream | Mode | What |
|--------|------|------|
| `me` | `auto_subscribe` | Own `users` + `user_profiles` only |
| `user_profile` | on-demand | Other profile by `user_id` param |

On-demand is lazy: nothing syncs until `db.syncStream('user_profile', { user_id }).subscribe()`. After `unsubscribe()`, rows stay for **TTL** (SDK default **24h**), then are removed. Use `{ ttl: 0 }` to drop immediately. Do **not** auto-subscribe all profiles — that would keep stale rows syncing indefinitely.

## Auth

`client_auth.jwks_uri` → Ktor `/.well-known/jwks.json`. JWT `aud` must be `zula` (see `JWT_AUDIENCE`).
