# Zula — Product vision

Canonical product framing for docs and implementation. Module guides implement this vision; if they diverge, update **this file** or the module doc in the same PR.

**Related:** [docs index](./README.md) · [implementation_plan.md](./implementation_plan.md) · [architecture.md](./architecture.md)

---

## 1. Vision & goal

Zula is a **social marketplace**: a personalized feed plus tools to match **suppliers and consumers**, negotiate, and close deals safely.

Closest analogs: **Booksy** (services) × **OLX / Facebook sales groups** (offers, local demand, import/resell communities) — without classifieds chaos and unsafe off-platform DMs.

**Primary goal:** simplify and protect the path from discovery → negotiation → handoff, for both sides.

---

## 2. Target audience

| Side | Who |
|------|-----|
| **Sellers** | Small service businesses, local specialists, craft / hobby makers selling **series and batches**, resellers and importers |
| **Buyers** | People looking for services or repeatable / distinctive products from independent suppliers |
| **Communities** | Groups analogous to FB import/resell/local-sourcing groups — shared space for posts, needs, and multi-party chat |

### Out of scope (product rule)

**Not** a general second-hand classifieds board. Reject / discourage one-off personal sales of single used items (e.g. “selling my old bike”). Listings should represent **ongoing activity**: services, batches, series, or clear supply-side offers — or buyer **needs** aimed at such suppliers.

---

## 3. Core model

### Bidirectional marketplace

| Surface | Actor | Meaning |
|---------|-------|---------|
| **Offers** (`feed` kind `offer`) | Seller | Services and **product batches** (not one-off junk drawer items) |
| **Needs** (`feed` kind `need`) | Buyer | Published demand; suppliers respond with offers / trade starts |
| **Trips** (`feed` kind `trip`) | Either | Travel/availability with coarse location — import runs, meetups on the road |
| **Either side initiates** | Both | Post offer **or** find clients; post need **or** find suppliers |

### Groups (communities)

Named communities (import, resell, craft, local services, …) where members:

- share group-scoped offers/needs,
- discuss in **multi-party** rooms (2+),
- discover suppliers/consumers in a trusted circle (FB-group job, without FB).

See [groups_module.md](./groups_module.md).

### Communication & deals

- **Chat** — rooms with **2+** participants: trade-scoped (MVP) and later group-scoped.
- **Negotiation** — price and terms inside chat + trade state machine (thin clients; rules on backend).
- **Trade templates** — including **barter / swap** and **cash meetup** coordination ([trade_module.md](./trade_module.md)). No in-app card payments in current plan; platform **coordinates** the deal and **verifies handoff**.
- **Safety** — blocks, moderation, dual trust scores, meetup **PIN/QR validation**, optional public disclosures — protect buyer **and** seller time/reputation (not escrow unless product later decides).

### Personalized feed

- Shared for-you surface for offers, needs, and trips.
- Ranking: interest / search / activity first; later optional external signals (Meta, Google, cookies) if privacy policy allows.
- Trait taxonomy + embeddings: [feed_module.md](./feed_module.md), [traits_module.md](./traits_module.md).

### Geolocation

Coarse **location tags** from **network fingerprints** — no continuous GPS on the server. Used for profile `location_tag`, trip posts, and local matching. [geolocation_module.md](./geolocation_module.md).

### Public seller presence

Seller profile + portfolio + trust/ratings: what a buyer sees before starting trade or chat. [seller_profile_module.md](./seller_profile_module.md), [profile_portfolio_module.md](./profile_portfolio_module.md).

---

## 4. AI — human-in-the-loop

AI is a **quiet assistant**, not an agent that owns the relationship.

| Capability | Intent | Doc home |
|------------|--------|----------|
| Auto-tag / categorize | Suggest traits, location, price band from text/images; **user confirms** | [media_module.md](./media_module.md), feed create |
| Feed matchmaking | Rank for-you from behavior + embeddings | feed |
| Contextual nudges | Suggest next steps in chat/trade UI (tooltips / action chips) — **post-MVP** | chat / trade polish |
| Description / question hints | Optional copy and buyer checklist hints — **post-MVP** | clients |

**Principles:** no ghostwritten conversations; reduce repetitive tagging/summary clicks; user always approves consequential actions.

---

## 5. Map to modules

| Product idea | Module doc |
|--------------|------------|
| Identity, trust, blocks, ratings | [user_module.md](./user_module.md), [trust_events.md](./trust_events.md) |
| Public seller page | [seller_profile_module.md](./seller_profile_module.md) |
| Portfolio / activity | [profile_portfolio_module.md](./profile_portfolio_module.md) |
| Offers, needs, trips, for-you | [feed_module.md](./feed_module.md), [traits_module.md](./traits_module.md) |
| Communities | [groups_module.md](./groups_module.md) |
| Uploads & image vectors | [media_module.md](./media_module.md) |
| Coarse location | [geolocation_module.md](./geolocation_module.md) |
| Deal lifecycle, **swap / meetup** templates | [trade_module.md](./trade_module.md) |
| Meetup PIN/QR | [validation_module.md](./validation_module.md) |
| Multi-party chat | [chat_module.md](./chat_module.md) |
| Reports / hide | [moderation_module.md](./moderation_module.md) |

Rollout order: [implementation_plan.md](./implementation_plan.md). **MVP includes groups** (membership + group feed in Wave 1; group chat in Wave 3) — not a post-launch add-on.
