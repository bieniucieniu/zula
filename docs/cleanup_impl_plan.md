# Cleanup impl plan (from review decisions)

Scope: simplify + harden what exists. Keep placeholders / future UI kits. No new product features. **Update contradicting docs in the same PR as each WP** (docs round Q15A).

---

## Decisions locked

### Structure / simplify (round 1)

| # | Item | Decision |
|---|------|----------|
| — | Empty feature packs | Keep placeholders |
| — | `core/contracts` + multi-bind | Remove + rewrite docs (docs Q1A) |
| — | `BlockResolver` always false | Remove with contracts |
| — | put/patch profile identical | Collapse to one write + scrub leftover PUT wording (docs Q6B) |
| — | JWT key zoo | Simplify to functions / fewer types |
| — | Dead auth APIs + phantom tests | Remove + schema/auth notes (docs Q9A) |
| — | Dual provider metadata | **Keep `AuthProvider.info()` as source** — skip collapse (docs Q2B) |
| — | Web dead OAuth client stack | Remove + doc server callback + `/oauth/complete` (docs Q12A) |
| — | `@zula/oauth` registry + unused `@zula/api` peerDep | Remove (docs Q13A — interpreted) |
| — | `SessionJwtIssuer` interface | Keep |
| — | Dead `OAuthSettings.kt` | Leave |
| — | Web action-group / layouts / form / lazy | Keep for future |
| — | `ProblemDetailsError` unused | Keep |
| — | Koin `get` / `inject` / `binds` style | OK |
| — | `DefaultKeysManager` + `KoinComponent` | OK |

### Behavior / correctness (round 2 picks)

| # | Pick | Decision |
|---|------|----------|
| 1 | C | AuthProvider strategy — leave as-is |
| 2 | A | Missing `SecurityConfig` → fail startup (no silent skip) |
| 3 | B | Cookie/Problem Koin `get()` on call — leave |
| 4 | A | Delete JWT access-token log line |
| 5 | A | Keep DB session check in JWT validate; document revoked ⇒ unauthenticated |
| 6 | A | Keep GET `/auth/session` refresh+cookies when JWT missing |
| 7 | C | Logout: soft provider check; logout never fails |
| 8 | B | Same URL trust policy: trust forwarded headers behind known proxy (OAuth + JWT issuer) |
| 9 | A | Wire `jwt.kubernetes.autoPull` (`enabled && autoPull`) |
| 10 | C | Fail fast if plugin deps missing (pairs with #2) |
| 11 | C | Dev auth: fixed `dev@…` only; drop `deviceInfo`-as-email |
| 12 | B | `@zula/api`: `createApiClient(opts)` factory; no module globals |
| 13 | A | Web 401 → refresh cookie path (parity with native) |
| 14 | B | Native: call `configureApiClient()` from app entry (no side-effect import) |
| 15 | B | Unify popup message type name (then WP6 deletes client path) |
| 16 | A | Empty JobRunr consumers — leave |
| 17 | A | `/jobs/ping` auth-protect (or admin-only) |
| 18 | A | Session response: real email claim (or rename field to match JWT) |
| 19 | A | Store + verify OAuth nonce vs id_token |
| 20 | A | Google refresh: revoke only on `invalid_grant` / explicit OAuth errors |
| 21 | A | Fail startup if require provider-refresh && encryption key missing |
| 22 | A | `ensureIdentity`: update scopes; reject identity owned by other user |
| 23 | A | Username create: catch unique violation → retry |
| 24 | A | OIDC JWKS cache: TTL / max-size eviction |
| 25 | A | Native OAuth: use platform client id (ios/android/web) |
| 26 | A | Ephemeral JWT keys default off outside dev |
| 27 | C | Separate cookie `secure` flag in config |

### Docs vs cleanup (round 3 picks)

Parsed from reply `1A 2B 2A 4A 5A 6B 7A 8A 9A 10A 11A 12A 14B 14A 15A` as:

`1A 2B 3A 4A 5A 6B 7A 8A 9A 10A 11A 12A 13A 14B 15A`

(Assumed typos: `2B 2A` → `2B 3A`; `14B 14A` → `13A 14B`. Say if wrong.)

| # | Pick | Decision |
|---|------|----------|
| 1 | A | Delete contract stubs now; rewrite architecture/conventions/module docs → add ports when 2nd caller exists |
| 2 | B | Keep `info()` as documented; **cancel WP5** (no config-only provider list) |
| 3 | A | Auth-protect `/jobs/ping`; patch `architecture.md` |
| 4 | A | Rewrite `clients.md` → web + Expo native + `@zula/*` packages (truth) |
| 5 | A | Docs describe both sign-in paths: web backend-popup; native PKCE/idToken → authenticate |
| 6 | B | Fix code KDoc + scrub any leftover PUT profile wording in module docs |
| 7 | A | `autoGenerateKey` off outside dev + update `secrets.md` |
| 8 | A | Wire `enabled && autoPull` + one line in `secrets.md` |
| 9 | A | WP4 delete leftover challenge SQL/repo + note in schema/auth docs |
| 10 | A | Ship auth-hardening WPs + short “Auth hardening” in `auth_and_permissions.md` / `secrets.md` |
| 11 | A | Fail-fast SecurityConfig/plugin deps + note in `architecture.md` |
| 12 | A | Delete SPA `/oauth/callback`; docs mention `/oauth/complete` + server `/auth/callback/google` |
| 13 | A | Remove `@zula/oauth` registry (unchanged from WP7) |
| 14 | B | Keep empty feature packs; add one line in `implementation_plan.md`: scaffolding present; contracts deferred until 2nd caller |
| 15 | A | Doc updates in **same PR** as each contradicting WP |

---

## Work packages

### WP1 — Kill unused contracts / stub ports (+ docs Q1A, Q14B)

**Do**
- Delete `core/contracts/{BlockResolver,TrustLedgerWriter,SellerActivityWriter}.kt`
- Strip `UserService` implements + Koin `binds`
- Drop stub methods (`resolveViewerBlock`, empty trust/seller TODOs)
- Keep profile write API only (WP2)
- **Docs (same PR):** rewrite `architecture.md` (tree, Koin examples, cross-feature table), `conventions.md` (prefer interfaces → add when 2nd caller), strip/adjust contract mentions in `feed_module.md`, `chat_module.md`, `user_module.md`, `moderation_module.md`, `seller_profile_module.md`, `auth_and_permissions.md`
- **Docs:** one line in `implementation_plan.md` — scaffolding present; contracts deferred until 2nd caller

**Done when:** no `core/contracts`; docs no longer mandate dead ports; compile green.

### WP2 — One profile write (+ docs Q6B)

**Do**
- Collapse `putMyProfile` / `patchMyProfile` → `updateMyProfile` (null = unchanged)
- Fix code KDoc; scrub leftover PUT full-replace wording in module docs if any
- No clear-field sentinel unless product needs later

**Done when:** one write entrypoint; docs/KDoc match PATCH/`UpdateMyProfile`.

### WP3 — Flatten JWT keys (+ autoPull + ephemeral default) (+ docs Q7A, Q8A)

**Do**
- Collapse key-manager zoo → config → key set → sign/verify
- Wire `autoPull`: pull only when `enabled && autoPull`
- Default `autoGenerateKey` off outside dev; fail loud if no keys in non-dev
- Keep `KoinComponent` / `SessionJwtIssuer` interface
- **Docs:** update `secrets.md` for defaults + `autoPull` conjunction

**Done when:** fewer files; secrets.md matches; key tests pass.

### WP4 — Dead auth surface (+ docs Q9A)

**Do**
- Remove `extendOAuthSession`, challenge insert/find/consume (+ SQLDelight if unused)
- Remove unused `withProviderAccess`
- Rewrite/delete phantom `AuthSessionLogicTest` / `resolveOAuthSession`
- **Docs:** note in `schema.md` / `auth_and_permissions.md` that leftover challenge storage removed

**Done when:** no dead challenge/extend APIs; docs note cleanup.

### WP5 — CANCELLED (docs Q2B)

Keep `AuthProvider.info()` as `GET /auth/providers` source per `user_module.md`. No config-only collapse.

### WP6 — Remove dead web OAuth client path (+ docs Q12A, Q5A)

**Do**
- Delete `popup-sign-in.ts`, `use-web-oauth-sign-in.ts`, route `/oauth/callback` (regen route tree)
- Keep `backend-popup-sign-in.ts` + `/oauth/complete`
- After WP8 message unify
- **Docs:** clients/auth — only `/oauth/complete` + server `/auth/callback/google`; both sign-in paths (web popup vs native authenticate)

**Done when:** no SPA `/oauth/callback`; docs match.

### WP7 — Slim `@zula/oauth` (+ docs Q13A)

**Do**
- Remove registry Map API + exports
- Drop unused `@zula/api` peerDependency
- Keep authorize-url / crypto / parse-response / react hook for native

**Done when:** package builds; native OAuth works.

### WP8 — Unify OAuth popup message type

**Do**
- One constant (`zula.oauth.complete`) in backend HTML + `backend-popup-sign-in.ts`
- Align then delete client duplicate in WP6

**Done when:** single message type string in repo.

### WP9 — Fail fast security / plugin deps (+ docs Q11A)

**Do**
- `configureAuthRouting`: require `SecurityConfig` — throw if missing
- Audit DB/JobRunr/Security: missing required dep → fail startup
- **Docs:** short boot/plugins note in `architecture.md`

**Done when:** misconfig crashes boot; architecture notes it.

### WP10 — Auth correctness: session email, nonce, identity, username (+ docs Q10A)

**Do**
- Real email in session response (not username claim)
- Persist + verify OAuth nonce vs id_token
- `ensureIdentity`: update scopes; reject foreign `user_id`
- Username unique-violation → retry
- Keep GET `/auth/session` refresh behavior
- Document JWT DB session check: revoked ⇒ unauthenticated
- **Docs:** “Auth hardening” subsection in `auth_and_permissions.md`

**Done when:** tests + hardening doc section.

### WP11 — Provider tokens / logout / Google refresh (+ docs Q10A)

**Do**
- Google refresh revoke only on `invalid_grant` / explicit OAuth errors
- Startup fail if require provider-refresh && encryption key unset
- Logout soft provider check; always revoke local session; never fail logout
- **Docs:** touch `auth_and_permissions.md` / `secrets.md` as needed

**Done when:** logout always succeeds locally; revoke policy tested; boot fails without key when required.

### WP12 — URL trust + cookie secure flag (+ docs Q10A)

**Do**
- Same forward-header trust for OAuth base URL and JWT issuer/audience
- Config flag `security.cookies.secure`; wire cookie helpers
- **Docs:** `secrets.md` / auth — cookie secure + Forwarded policy

**Done when:** one public-URL policy; cookies use config flag; docs updated.

### WP13 — Delete token log

**Do**
- Remove `log.info("token: $token")` from JWT plugin

**Done when:** grep clean.

### WP14 — Dev auth fixed email

**Do**
- Fixed `dev@zula.local` only; ignore `deviceInfo` as email
- Update provider + tests

**Done when:** only fixed email.

### WP15 — Jobs ping auth (+ docs Q3A); consumers leave

**Do**
- Protect `GET /jobs/ping` with JWT (or admin if exists)
- Leave empty feature consumers
- **Docs:** patch `architecture.md` smoke → authenticated

**Done when:** unauth → 401; architecture.md updated.

### WP16 — OIDC JWKS cache eviction (+ docs Q10A)

**Do**
- TTL and/or max entries on key cache
- Mention in auth hardening docs if relevant

**Done when:** stale kids eventually drop.

### WP17 — `@zula/api` client factory + web 401 + native entry (+ docs Q4A, Q5A)

**Do**
- `createApiClient(opts)` / explicit set-at-boot (no accidental import-order globals)
- Web: cookie mode + 401 → `POST /auth/refresh` → retry
- Native: configure from app entry
- **Docs:** rewrite `clients.md` to web + Expo native + `@zula/api` / `@zula/oauth`; both sign-in paths

**Done when:** clients.md matches reality; typecheck green.

### WP18 — Native platform OAuth client id (+ docs Q4A)

**Do**
- Pick ios/android/web client id by `Platform.OS`
- Document fallback in clients.md or native readme pointer

**Done when:** platform ids used; docs mention.

---

## Suggested order

| Phase | WPs | Why |
|-------|-----|-----|
| A — quick wins | WP13, WP8 | token leak; message unify |
| B — dead client code | WP6, WP7 | deletes + clients/oauth docs |
| C — user/contracts | WP1, WP2 | contracts gone + doc rewrite |
| D — auth dead | WP4 | SQL/API + schema note (**no WP5**) |
| E — fail-fast / config | WP9, WP12, WP3 | boot + keys + cookies/URL + secrets.md |
| F — auth correctness | WP10, WP11, WP14, WP15, WP16 | security + hardening docs |
| G — clients | WP17, WP18 | api factory + `clients.md` rewrite |

Commit per WP (docs in same commit/PR as code). After each phase: server tests + web/native typecheck.

---

## Explicit non-goals

- Implementing empty feature pack bodies
- Rewriting AuthProvider strategy
- Removing JobRunr / empty consumers
- Changing GET `/auth/session` refresh-on-missing-JWT
- Replacing Cookie/Problem Koin `get()`
- Collapsing provider list to config-only (**WP5 cancelled**)
- Building product UI on action-group / layouts
- Compose/SwiftUI client apps (docs stop claiming them as current)
