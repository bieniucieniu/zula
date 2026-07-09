# Guide: Geolocation Module

**Coarse travel/location tags** from network fingerprint shifts — **no continuous GPS** on server.

**Status:** Doc complete · **Backend:** ⬜ · **Service:** `GeolocationService` (planned)

**Depends on:** [user_module.md](./user_module.md) (`location_tag` column exists) · **Unblocks:** trip feed context (feed Wave 2)

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave 2.2 (`geo-A`)

**Related:** [schema.md](./schema.md#geolocation-module-planned--wave-2) · [README.md](../README.md) · [conventions.md](./conventions.md)

---

## Goals

| Goal | Detail |
|------|--------|
| **Client fingerprints** | Anonymized network snapshots on schedule or change |
| **Coarse label** | Resolver → `user_profiles.location_tag` |
| **Trip posts** | Optional denormalized tag on feed trip items |
| **Privacy** | No raw GPS; TTL on fingerprint events |

---

## Architecture

```mermaid
sequenceDiagram
    participant C as Client
    participant G as GeolocationService
    participant DB as PostgreSQL

    C->>G: ReportNetworkFingerprint (auth)
    G->>DB: INSERT network_fingerprint_events
    G->>G: Resolve hash → city/region label
    G->>DB: UPDATE user_profiles.location_tag
```

---

## Schema

| Table | Purpose |
|-------|---------|
| `network_fingerprint_events` | Hashed fingerprints, jsonb metadata, TTL purge |

Writes to existing `user_profiles.location_tag`. Optional `feed_items.origin_location_tag` at post time.

**Next migration:** `000004_geolocation.up.sql` — [schema.md](./schema.md#geolocation-module-planned--wave-2)

---

## Proto / RPC surface

| RPC | Auth |
|-----|------|
| `ReportNetworkFingerprint` | Auth, rate limited |

---

## Auth policy

Authenticated users only; rate limited per user/IP. [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

### Phase geo-A — Ingest & profile tag

- [ ] `network_fingerprint_events` table
- [ ] `ReportNetworkFingerprint` RPC
- [ ] Resolver stub (rule table or external API interface)
- [ ] Update `location_tag` on significant change
- [ ] Tests: `apps/backend/tests/geolocation/`

### Phase geo-B — Feed integration

- [ ] Snapshot tag on trip `feed_items` at create time

### Phase geo-C — Hardening

- [ ] TTL purge job
- [ ] User opt-out on profile
- [ ] Threat model doc section (no precise tracking)

---

## Verification

```bash
devenv test
cd apps/backend && go test ./tests/geolocation/...
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `apps/backend/db/migration/000004_geolocation.up.sql` | Events table |
| `apps/backend/internal/apitypes/` | Report RPC |
| `apps/backend/internal/service/geolocation.go` | Ingest + resolve |
| `docs/geolocation_module.md` | This guide |

---

## Open questions

| Question | MVP default |
|----------|-------------|
| Resolver accuracy | City-level string; no lat/long storage |
| Web vs mobile fingerprints | Same RPC; different client payload shapes |
| Opt-out default | Opt-in to fingerprint reporting in client settings |

---

## Related documentation

- [seller_profile_module.md](./seller_profile_module.md) — displays `location_tag`
- [feed_module.md](./feed_module.md) — trip posts
