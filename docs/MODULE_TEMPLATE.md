# Module documentation template

Use this structure for every `docs/*_module.md`. Short modules may omit deep SQL/OpenAPI sections but **must keep the same headings** (mark N/A where empty).

---

```markdown
# Guide: {Module Name}

One-line purpose.

**Status:** {Doc: complete | draft} · **Backend:** {none | partial | MVP | done} · **Feature:** `features:{name}`

**Depends on:** [module](./other_module.md) · **Unblocks:** [module](./other_module.md)

**Master plan:** [implementation_plan.md](./implementation_plan.md) Wave X.Y

**Related:** [architecture.md](./architecture.md) · [conventions.md](./conventions.md) · [auth_and_permissions.md](./auth_and_permissions.md) · [schema.md](./schema.md)

---

## Goals

| Goal | Detail |
|------|--------|
| … | … |

---

## Architecture

```text
(diagram or mermaid)
```

Feature layout:

| Layer | File | Purpose |
|-------|------|---------|
| Routing | `{Name}Routing.kt` | HTTP paths, OpenAPI metadata |
| Service | `{Name}Service.kt` | Business logic, transactions |
| Integration | `{Name}Consumer.kt` / `{Name}Publisher.kt` | RabbitMQ boundaries |

---

## Schema

Tables owned by this module. **Canonical DDL:** [schema.md](./schema.md#{anchor}) — SQLDelight `.sq` in `core/database/`.

| Table | Purpose |
|-------|---------|
| … | … |

**Next migration:** `00000N_*.sql` (if not yet shipped)

---

## REST / OpenAPI surface

Link: [api_index.md](./api_index.md). Summarize only module-specific routes and DTOs.

---

## Auth policy

| Route | Auth | Notes |
|-------|------|-------|
| … | public / auth / admin | … |

Full matrix: [auth_and_permissions.md](./auth_and_permissions.md)

---

## Implementation phases

Use stable IDs: `{module}-{phase}` (e.g. `feed-A`, `trade-B`).

### Phase A — …

- [ ] …

### Shipped (optional)

Move completed phases here with `[x]` and date.

---

## Verification

```bash
./gradlew :core:database:generateSqlDelightInterface
./gradlew :features:{name}:test
./gradlew test
```

---

## File checklist

| Path | Purpose |
|------|---------|
| `features/{name}/src/main/kotlin/.../{Name}Routing.kt` | HTTP routes |
| `features/{name}/src/main/kotlin/.../{Name}Service.kt` | Business logic |
| `features/{name}/src/main/kotlin/.../{Name}Publisher.kt` | MQ publish (if any) |
| `features/{name}/src/main/kotlin/.../{Name}Consumer.kt` | MQ consume (if any) |
| `core/database/src/main/sqldelight/.../*.sq` | SQLDelight queries |
| `features/{name}/src/test/kotlin/...` | Tests |

---

## Open questions

| Question | MVP default |
|----------|-------------|
| … | … |

---

## Related documentation

- …
```

---

## Status legend

| Backend | Meaning |
|---------|---------|
| ⬜ none | No production code |
| 🔶 partial | Some phases shipped; see module phases |
| ✅ MVP | Core routes usable; later phases deferred |
| ✅ done | All planned phases for current wave |

Update status in **this file**, **`docs/README.md`**, and **`implementation_plan.md`** when merging backend work.
