# Zula — Product vision

Canonical product framing for docs and implementation. Module guides follow this vision.

**Related:** [docs index](./README.md) · [implementation_plan.md](./implementation_plan.md) · [architecture.md](./architecture.md)

---

## 1. Vision & goal

Zula is a **starter kit** for small companies: a simple base app they can extend.

**Primary goal:** sign-in, a public company profile, and web + mobile clients on one API — ready to build on.

---

## 2. Who it is for

Small companies that need their own app, not a hosted social network.

| Piece | Who uses it |
|-------|-------------|
| **Kit** | The company building on Zula |
| **Profile** | The company’s public page |
| **Clients** | Staff and customers of that company |

---

## 3. What is in the kit

| Piece | Meaning | Doc |
|-------|---------|-----|
| **Auth** | Google, Apple, web session, native bearer, local dev bypass | [user_module.md](./user_module.md) |
| **Profile** | Public page, bio, portfolio, pins | [seller_profile_module.md](./seller_profile_module.md), [profile_portfolio_module.md](./profile_portfolio_module.md) |
| **Media** | Image upload and download | [media_module.md](./media_module.md) |
| **Web** | React app shell | [clients.md](./clients.md) |
| **Native** | Expo app; sign-in shipped | [clients.md](./clients.md) |
| **API client** | `@zula/api`, `@zula/oauth` | [clients.md](./clients.md) |

---

## 4. Out of scope

Not a social network, community feed, or classifieds marketplace.

No likes, comments, bumps, bookmarks, or for-you ranking as product. No groups, deal negotiation, or meetup coordination as product.

Code and module notes for those areas may still sit in the repo. They are **not** the starter kit. Do not extend them as the product.

---

## 5. Map

| Idea | Doc |
|------|-----|
| Identity | [user_module.md](./user_module.md) |
| Public profile | [seller_profile_module.md](./seller_profile_module.md), [profile_portfolio_module.md](./profile_portfolio_module.md) |
| Uploads | [media_module.md](./media_module.md) |
| Web + native | [clients.md](./clients.md) |

Rollout: [implementation_plan.md](./implementation_plan.md).
