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

- `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM` (RSA session JWT; see below)
- `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `PG_PASSWORD`, OAuth client secrets

Legacy `JWT_SECRET` (HMAC) is replaced by asymmetric JWT keys in `core/security`.

**ConfigMap (GitOps, not Infisical):** `APP_URL`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_PUBLIC_URL` (optional), `PG_HOST`, `PG_PORT`, `PG_DATABASE`, `PG_USER`, `HTTP_PORT`, `JWT_AUTO_GENERATE_KEY=false`.

### Session JWT keys (RSA)

| Variable | Purpose |
|----------|---------|
| `JWT_PRIVATE_KEY_PEM` | Signs session tokens (PKCS#8 PEM). Required on auth-serving pods. |
| `JWT_PUBLIC_KEY_PEM` | Verifies tokens. Optional if private key is set (public is derived). Required alone for verify-only replicas. |
| `JWT_AUTO_GENERATE_KEY` | `false` in cluster (default locally: `true`). Never rely on ephemeral keys in production. |
| `JWT_AUDIENCE` | Expected `aud` claim (default `zula`). |

Generate a Kubernetes Secret manifest from the repo:

```bash
./gradlew :server:generateJwtK8sSecret \
  -PjwtSecretNamespace=zula \
  -PjwtSecretName=zula-jwt-keys \
  > deploy/k8s/zula-jwt-keys.secret.yaml
```

Or use the keys manager CLI (same entrypoint):

```bash
# print manifest
./gradlew :server:manageJwtKeys -PjwtKeysCommand=generate

# push a fresh key pair to the in-cluster / kubeconfig API
./gradlew :server:manageJwtKeys -PjwtKeysCommand=push \
  -PjwtK8sEnabled=true -PjwtSecretNamespace=zula

# pull and print manifest
./gradlew :server:manageJwtKeys -PjwtKeysCommand=pull \
  -PjwtK8sEnabled=true -PjwtSecretNamespace=zula
```

In-cluster bootstrap (same namespace as the backend):

```yaml
security:
  jwt:
    autoGenerateKey: false
    kubernetes:
      enabled: true
      secretName: zula-jwt-keys
      autoPull: true      # load secret when env PEMs are absent
      autoPush: false     # set true only for one-shot bootstrap jobs
```

| Variable | Purpose |
|----------|---------|
| `JWT_K8S_ENABLED` | Enable Kubernetes API integration for JWT keys |
| `JWT_K8S_NAMESPACE` | Secret namespace (defaults to pod namespace) |
| `JWT_K8S_SECRET_NAME` | Secret name (default `zula-jwt-keys`) |
| `JWT_K8S_AUTO_PULL` | Pull secret on startup when env keys missing |
| `JWT_K8S_AUTO_PUSH` | Push freshly generated keys to secret |

Apply in the **app namespace** (same namespace as the backend Service):

```bash
kubectl apply -f deploy/k8s/zula-jwt-keys.secret.yaml
```

Wire the backend Deployment (GitOps):

```yaml
envFrom:
  - secretRef:
      name: zula-jwt-keys
```

Example stub: [deploy/k8s/jwt-keys-secret.example.yaml](../deploy/k8s/jwt-keys-secret.example.yaml).

Verify-only pods (e.g. read replicas that validate JWT but never issue) can mount **only** `JWT_PUBLIC_KEY_PEM` and omit the private key.

### S3 / MinIO

| Variable | Purpose |
|----------|---------|
| `S3_ENDPOINT` | API host the **backend** uses (e.g. `http://minio:9000` in-cluster) |
| `S3_PUBLIC_URL` | Browser-reachable base for presigned PUT + image URLs (e.g. `https://s3.kurwidolek.com/zula`) |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | Must match the MinIO root user (Infisical). Fallbacks: `AWS_ACCESS_KEY_ID`, `MINIO_ROOT_USER` |

If uploads fail with *Access Key Id does not exist*, the secret keys in Infisical do not match the MinIO deployment — rotate them together.
