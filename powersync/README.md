# PowerSync (self-hosted)

Client schema: `packages/powersync` (`@zula/powersync`).

## Layout

| Path | Role |
|------|------|
| `sync-config.yaml` | Sync Streams (shared) |
| `dev/docker-compose.yaml` | Local PowerSync on `:8081`; builds URI env from secretspec `PS_*` |
| `dev/service.yaml` | `!env PS_DATA_SOURCE_URI` / `PS_STORAGE_URI` / `PS_JWKS_URI` |
| `prod/docker-compose.yaml` | Smoke-test reference only — **k8s is real deploy** |
| `prod/service.yaml` | Prod config via `!env PS_*` (k8s Secret/ConfigMap) |

Postgres (devenv): `zula` (source), `zula_powersync` (buckets).  
Replication role + publication: `services.postgres.initialDatabases.*.initialSQL` in [`devenv.nix`](../devenv.nix).

## Dev

```bash
devenv --profile powersync up   # uses powersync/dev/compose
bun run gen:powersync
```

`dev/docker-compose.yaml` builds container env from secretspec (defaults = `secretspec.toml`):

| secretspec | Used for |
|------------|----------|
| `PS_URL` | Client origin (`http://127.0.0.1:8081` ↔ compose port `8081`) |
| `PS_REPLICATION_PASSWORD` | `PS_DATA_SOURCE_URI` (`powersync_role`) |
| `PS_STORAGE_USERNAME` / `PS_STORAGE_PASSWORD` | `PS_STORAGE_URI` |
| _(compose default)_ | `PS_JWKS_URI` → `http://host.docker.internal:8080/.well-known/jwks.json` |

## Prod (k8s)

Ship `prod/service.yaml` + `sync-config.yaml` as ConfigMap/volume. Set:

| Env | Purpose |
|-----|---------|
| `PS_DATA_SOURCE_URI` | App Postgres (replication) |
| `PS_DATA_SOURCE_SSLMODE` | e.g. `require` |
| `PS_STORAGE_URI` | Bucket-storage Postgres |
| `PS_STORAGE_SSLMODE` | e.g. `require` |
| `PS_JWKS_URI` | Public JWKS URL |

Compose under `prod/` is **not** the deployment path — GitOps/k8s is.

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
