# PowerSync (self-hosted)

Client schema: `packages/powersync` (`@zula/powersync`).

## Layout

| Path | Role |
|------|------|
| `sync-config.yaml` | Sync Streams |
| `docker-compose.yaml` | Local PowerSync on `:8081`; builds URI env from secretspec `PS_*` |
| `service.yaml` | `!env PS_DATA_SOURCE_URI` / `PS_STORAGE_URI` / `PS_JWKS_URI` |

Postgres (devenv): `zula` (source), `zula_powersync` (buckets).  
Replication role + publication: `services.postgres.initialDatabases.*.initialSQL` in [`devenv.nix`](../devenv.nix).
`ALTER DEFAULT PRIVILEGES FOR ROLE <app user>` so tables created by Ktor migrations are selectable by `powersync_role`.
Server also re-grants `SELECT` on all public tables after migrate (fixes DBs already initialized).

Deploy/k8s uses a separate config — not these local files.

## Run

```bash
devenv --profile powersync up
bun run gen:powersync
```

Compose builds container env from secretspec (defaults = `secretspec.toml`):

| secretspec | Used for |
|------------|----------|
| `PS_URL` | Client origin (`http://127.0.0.1:8081` ↔ compose port `8081`) |
| `PS_REPLICATION_PASSWORD` | `PS_DATA_SOURCE_URI` (`powersync_role`) |
| `PS_STORAGE_USERNAME` / `PS_STORAGE_PASSWORD` | `PS_STORAGE_URI` |
| _(compose default)_ | `PS_JWKS_URI` → `http://host.docker.internal:8080/.well-known/jwks.json` |

`devenv enterTest` asserts those secretspec defaults are set.

## Fresh Postgres

`wal_level=logical` + second DB apply on **first** init only:

```bash
devenv down
rm -rf .devenv/state/postgres
devenv --profile powersync up
```

## Sync design (profiles)

| Stream | Mode | What |
|--------|------|------|
| `me` | `auto_subscribe` | Own `users` + `user_profiles` |
| `user_profile` | on-demand | Profile by `user_id` |

On-demand + TTL (default 24h after unsubscribe). Never auto-subscribe all profiles.

## Auth

JWKS from Ktor; JWT `aud` = `zula`.

## Upload

`POST /api/sync/batch` — see [docs/powersync.md](../docs/powersync.md). Registry → `UserProfileWriter` for `user_profiles`.
