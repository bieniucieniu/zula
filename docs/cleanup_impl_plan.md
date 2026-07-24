# Cleanup impl plan (from review decisions)

Scope: simplify + harden what exists. Keep placeholders / future UI kits. No new product features.

---

## Decisions locked

### Structure / simplify (round 1)

| # | Item | Decision |
|---|------|----------|
| — | Empty feature packs | Keep placeholders |
| — | `core/contracts` + multi-bind | Remove |
| — | `BlockResolver` always false | Remove with contracts |
| — | put/patch profile identical | Collapse to one write |
| — | JWT key zoo | Simplify to functions / fewer types |
| — | Dead auth APIs + phantom tests | Remove |
| — | Dual provider metadata | Collapse to one source |
| — | Web dead OAuth client stack | Remove |
| — | `@zula/oauth` registry + unused `@zula/api` peerDep | Remove |
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

---

## Work packages

### WP1 — Kill unused contracts / stub ports

**Do**
- Delete `core/contracts/{BlockResolver,TrustLedgerWriter,SellerActivityWriter}.kt`
- Strip `UserService` implements + Koin `binds`
- Drop stub methods (`resolveViewerBlock`, empty trust/seller TODOs)
- Keep profile write API only (WP2)

**Done when:** no multi-interface binds for user; no `core/contracts`; compile green.

### WP2 — One profile write

**Do**
- Collapse `putMyProfile` / `patchMyProfile` → `updateMyProfile` (null = unchanged)
- Update `UserProfileWriter` + docs that claim PUT full-replace
- No clear-field sentinel unless product needs later

**Done when:** one write entrypoint; no duplicate bodies.

### WP3 — Flatten JWT keys (+ autoPull + ephemeral default)

**Do**
- Collapse `KeysManager` / `KeysManagers` / `DefaultKeysManager` / dual `JwtKeys`+`JwtKeySet`+`toLegacyJwtKeys` → config → key set → sign/verify
- Keep used paths: k8s / PEM / JSON / auto-generate
- Wire `autoPull`: k8s pull only when `enabled && autoPull` (#9)
- Default `autoGenerateKey` off outside dev (#26); fail loud if no keys in non-dev
- Keep `KoinComponent` / `get<Json>()`; keep `SessionJwtIssuer` interface

**Done when:** fewer files; one key model; autoPull honored; key tests pass.

### WP4 — Dead auth surface

**Do**
- Remove `extendOAuthSession`, challenge insert/find/consume (+ SQLDelight if unused)
- Remove unused `withProviderAccess`
- Rewrite/delete `AuthSessionLogicTest` inventing `resolveOAuthSession`
- Grep before schema drop

**Done when:** no dead challenge/extend APIs; tests cover real code only.

### WP5 — One provider metadata source

**Do**
- Single source for `/auth/providers` (prefer config-driven `configuredProviders`)
- Stop duplicating scopes/display in `*AuthProvider.info()` if config owns them
- Leave AuthProvider strategy hierarchy as-is (#1C)

**Done when:** one function builds provider list; no drift.

### WP6 — Remove dead web OAuth client path

**Do**
- Delete `popup-sign-in.ts`, `use-web-oauth-sign-in.ts`, route `/oauth/callback` (regen route tree)
- Keep `backend-popup-sign-in.ts` + `/oauth/complete`
- Depends on WP8 (message-type unify) first or do unify inside survivors only

**Done when:** login via backend popup; no `/oauth/callback`.

### WP7 — Slim `@zula/oauth`

**Do**
- Remove registry Map API + exports
- Drop unused `@zula/api` peerDependency
- Keep authorize-url / crypto / parse-response / react hook for native

**Done when:** package builds; native OAuth works.

### WP8 — Unify OAuth popup message type (#15B)

**Do**
- One constant (prefer `zula.oauth.complete`) in backend HTML + `backend-popup-sign-in.ts`
- Align any remaining client popup before WP6 deletes it (or delete first then only one name remains — still rename server/client survivor to same string)

**Done when:** single message type string in repo.

### WP9 — Fail fast security / plugin deps (#2A, #10C)

**Do**
- `configureAuthRouting`: require `SecurityConfig` — throw / `error()` if missing (no silent return)
- Audit related plugins (DB, JobRunr, Security): missing required dep → fail startup, not soft skip
- Keep intentional optionals explicit (e.g. DB off = health degraded only if product allows)

**Done when:** misconfig crashes boot; auth routes never silently vanish.

### WP10 — Auth correctness: session email, nonce, identity, username (#18–23)

**Do**
- `#18`: put real email in session response (JWT email claim or load from DB); stop labeling username as email — pick one field name and stick to it
- `#19`: persist OAuth nonce with state; verify against id_token `nonce`
- `#22`: `ensureIdentity` update scopes; reject if existing identity `user_id` ≠ current user
- `#23`: username insert catch unique violation → retry loop
- Leave GET `/auth/session` refresh behavior (#6A)
- Document JWT DB session check: revoked/missing session ⇒ no principal (#5A)

**Done when:** tests cover nonce verify, identity ownership, username retry; session email correct.

### WP11 — Provider tokens / logout / Google refresh (#7C, #20A, #21A)

**Do**
- `#20`: Google refresh revoke only on `invalid_grant` / explicit OAuth error bodies; network/5xx do not revoke
- `#21`: startup fail if `requireProviderRefreshOnLogin` (or equivalent) && encryption key unset
- `#7`: logout soft provider check — errors logged; local session always revoked; logout never fails user

**Done when:** logout always 2xx on valid session cookie; refresh revoke policy tested; boot fails without key when required.

### WP12 — URL trust + cookie secure flag (#8B, #27C)

**Do**
- Same forward-header trust for OAuth redirect base URL and JWT issuer/audience construction (trust `X-Forwarded-*` / Host behind known proxy)
- Add config flag for cookie `secure` (e.g. `security.cookies.secure`); wire cookie helpers; document local http vs prod https

**Done when:** one public-URL helper/policy; cookies use config flag.

### WP13 — Delete token log (#4A)

**Do**
- Remove `log.info("token: $token")` from JWT plugin (`Security.kt`)

**Done when:** no access token in logs; grep clean.

### WP14 — Dev auth fixed email (#11C)

**Do**
- Dev bypass: fixed `dev@zula.local` (or existing default); ignore `deviceInfo` as email
- Update `DevAuthProvider` + tests; remove “custom email via deviceInfo” test

**Done when:** only fixed email; tests updated.

### WP15 — Jobs ping auth (#17A); consumers leave (#16A)

**Do**
- Protect `GET /jobs/ping` with JWT (or admin role if exists — else authenticated user minimum)
- Leave empty feature consumers registered

**Done when:** unauthenticated ping → 401; authed still enqueues.

### WP16 — OIDC JWKS cache eviction (#24A)

**Do**
- TTL and/or max entries on `OidcIdTokenVerifier` key cache; evict stale kids

**Done when:** rotated-away keys eventually drop; test or clear comment on TTL.

### WP17 — `@zula/api` client factory + web 401 + native entry (#12B, #13A, #14B)

**Do**
- Replace module globals with `createApiClient(opts)` (baseUrl, authMode, getAccessToken, setAccessToken, onUnauthorized)
- Orval mutator uses injected client instance (or thin adapter set once at boot via explicit `setApiClient`)
- Prefer: factory returns client; `setDefaultApiClient(client)` once at app entry — still explicit, not import-order accidental
- Web boot: create cookie-mode client + `onUnauthorized` → `POST /auth/refresh` (credentials include) → retry
- Native boot: call configure/create from app entry (not side-effect `import "@/lib/api"`)
- Update web/native AuthProvider + session storage to use new API

**Done when:** no hidden mutator globals; web 401 refreshes; native entry configures visibly; typecheck green.

### WP18 — Native platform OAuth client id (#25A)

**Do**
- `use-native-oauth-sign-in` / config: pick ios vs android vs web client id by `Platform.OS`
- Keep fallback chain documented

**Done when:** ios/android builds use platform client ids; web Expo uses web id.

---

## Suggested order

| Phase | WPs | Why |
|-------|-----|-----|
| A — quick wins | WP13, WP8 | token leak; message unify |
| B — dead client code | WP6, WP7 | low risk deletes |
| C — user/contracts | WP1, WP2 | small server |
| D — auth dead + meta | WP4, WP5 | schema/API cleanup |
| E — fail-fast / config | WP9, WP12, WP3 | boot + keys + cookies/URL |
| F — auth correctness | WP10, WP11, WP14, WP15, WP16 | security logic |
| G — clients | WP17, WP18 | api factory + native ids |

Commit per WP (or tight pairs). After each phase: server tests + web/native typecheck.

---

## Explicit non-goals (this plan)

- Implementing empty feature pack bodies (chat/feed/…)
- Rewriting AuthProvider strategy (#1C leave)
- Removing JobRunr / empty consumers (#16A)
- Changing GET `/auth/session` refresh-on-missing-JWT (#6A keep)
- Replacing Cookie/Problem Koin `get()` (#3B leave)
- Building product UI on action-group / layouts
