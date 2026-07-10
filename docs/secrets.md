# Secrets and client URLs

Kubernetes deployment (Flux, Gateway routes, Infisical operator) lives in a **separate GitOps repo** — not in this project.

## URL env vars

| Variable | Used by | Purpose |
|----------|---------|---------|
| `APP_URL` | Backend ConfigMap (GitOps) | Public HTTP origin: OAuth callbacks, `/health`, web UI |
| `API_URL` | Mobile clients only | REST API base URL (`https://host/api/v1`) |

Backend accepts `APP_URL`; `HOST_BASE_URL` still works as a fallback.

## Production domains

| Host | Role |
|------|------|
| `https://zula.kurwidolek.com` | Web UI + HTTP API (`APP_URL`) |

### Routing (GitOps repo)

| Hostname | Purpose | Protocol | Paths | Backend port |
|----------|---------|----------|-------|--------------|
| `zula.kurwidolek.com` | Web static + HTTP API | HTTP | `/` → web; `/api/**`, `/health` → backend | web:8080; backend:8080 |

OAuth redirect URIs (Google / Apple):

- `https://zula.kurwidolek.com/api/auth/callback/google`
- `https://zula.kurwidolek.com/api/auth/callback/apple`

Cloudflare tunnel (manual): both hosts → Traefik `:80`.

## Local development

```properties
# packages/client-config/dev.properties (Android emulator default)
API_URL=http://10.0.2.2:8080/api/v1
APP_URL=http://10.0.2.2:8080
```

Override via root `.env` for **iOS debug** (simulator or LAN IP), then run `gen-client-config`. Values must use `http://` or `https://` with `/api/v1` path.

## Infisical (credentials only)

Sync to `Secret/backend-secrets` in the GitOps repo:

- `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` (RSA PEM, RS256), `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `PG_PASSWORD`, OAuth client secrets

Optional JWT tuning (ConfigMap or secrets):

- `JWT_ISSUER` — defaults to `APP_URL`
- `JWT_AUDIENCE` — defaults to `zula-api`
- `JWT_KEY_ID` — JWKS `kid`, defaults to `zula-1`

**ConfigMap (GitOps, not Infisical):** `APP_URL`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_PUBLIC_URL` (optional), `PG_HOST`, `PG_PORT`, `PG_DATABASE`, `PG_USER`, `HTTP_PORT`.

### S3 / MinIO

| Variable | Purpose |
|----------|---------|
| `S3_ENDPOINT` | API host the **backend** uses (e.g. `http://minio:9000` in-cluster) |
| `S3_PUBLIC_URL` | Browser-reachable base for presigned PUT + image URLs (e.g. `https://s3.kurwidolek.com/zula`) |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | Must match the MinIO root user (Infisical). Fallbacks: `AWS_ACCESS_KEY_ID`, `MINIO_ROOT_USER` |

If uploads fail with *Access Key Id does not exist*, the secret keys in Infisical do not match the MinIO deployment — rotate them together.
