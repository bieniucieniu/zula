# Trust ledger events

Immutable audit trail in `user_trust_ledger`. Cache in `user_stats.implicit_trust_score` must stay in sync when applying deltas.

**Writers:** UserService internal helpers only — trade calls these, not raw SQL from handlers.

---

## Event catalog

| `event_type` | Typical `delta` | Writer module | When |
|--------------|-----------------|---------------|------|
| `SEED` | 0 | user | Account creation (optional log row) |
| `ADMIN_ADJUST` / `ADMIN_ADJUSTMENT` | ±N | user (admin) | `UpdateImplicitTrust` via `ApplyTrustEvent` |
| `TRADE_COMPLETED` | +10 … +20 | trade → user | Trade closed |
| `TRADE_CANCELLED` | 0 or −5 | trade → user | Cancel after accept (policy TBD) |
| `TRADE_NO_SHOW` | −15 | trade → user | Failed meetup (dispute path) |
| `RATING_RECEIVED` | 0 | — | Ratings affect **explicit** avg only via `user_ratings` |
| `MODERATION_PENALTY` | −N | moderation | Admin action (Wave 5) |
| `REPORT_ABUSE` | −N | moderation | Sustained valid reports (later) |

Exact deltas are configured in service code — this table is the **naming contract**. Add new types here before using them in SQL.

---

## Explicit vs implicit trust

| Metric | Source | Recalculation |
|--------|--------|---------------|
| **Explicit rating** | `user_ratings` | Time-decayed average; lazy refresh on profile read (1h TTL) |
| **Implicit trust** | `user_trust_ledger` sum + base 100 | Lazy refresh on profile read; immediate cache bump on write |

Peer ratings: internal `recordPeerRating`.

---

## Write pattern

```text
1. INSERT user_trust_ledger (user_id, delta, event_type, description)
2. UPDATE user_stats SET implicit_trust_score = implicit_trust_score + delta
   (same transaction)
```

Trade completion calls UserService — see [user_module.md](./user_module.md) Step 3.

---

## Related

- [user_module.md](./user_module.md) — cache and lazy recalc
- [trade_module.md](./trade_module.md) — lifecycle
- [moderation_module.md](./moderation_module.md) — penalties
