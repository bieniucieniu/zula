# Module documentation template

Use this structure for every `docs/*_module.md`. Short modules may omit deep SQL/proto sections but **must keep the same headings** (mark N/A where empty).

---

```markdown
# Guide: {Module Name}

One-line purpose.

**Status:** {Doc: complete | draft} · **Backend:** {none | partial | MVP | done} · **Service:** `{ServiceName}`

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

---

## Schema

Tables owned by this module. **Canonical DDL:** [schema.md](./schema.md#{anchor}) — do not duplicate full `CREATE TABLE` here unless teaching “from zero”.

| Table | Purpose |
|-------|---------|
| … | … |

**Next migration:** `00000N_*.up.sql` (if not yet shipped)

---

## Proto / RPC surface

Link: [api_index.md](./api_index.md). Summarize only module-specific messages.

---

## Auth policy

| RPC | Auth | Notes |
|-----|------|-------|
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
# devenv shell
gen-openapi
cd apps/backend && sqlc generate
devenv test
cd apps/backend && go test ./tests/{area}/...
```

---

## File checklist

| Path | Purpose |
|------|---------|
| … | … |

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
| ✅ MVP | Core RPCs usable; later phases deferred |
| ✅ done | All planned phases for current wave |

Update status in **this file**, **`docs/README.md`**, and **`implementation_plan.md`** when merging backend work.
