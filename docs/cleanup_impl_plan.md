# Cleanup impl plan (from review decisions)

Scope: simplify what exists. Keep placeholders / future UI kits. No new features.

Related review themes: empty feature packs stay; Koin/service-locator ok for now; `SessionJwtIssuer` interface stays; `OAuthSettings.kt` stays; `ProblemDetailsError` stays; web `action-group` / layouts / form / lazy stay.

---

## Decisions locked

| Item | Decision |
|------|----------|
| Empty feature packs | Keep as placeholders |
| `core/contracts` + multi-bind | Remove (not useful yet) |
| `BlockResolver` always false | Remove with contracts |
| put/patch profile identical | Collapse to one write path |
| JWT key zoo | Simplify to functions / fewer types |
| Dead auth APIs (challenges, `extendOAuthSession`, `withProviderAccess`) + phantom test helpers | Remove |
| Dual provider metadata | Collapse to one source |
| Web dead OAuth client stack | Remove |
| `@zula/oauth` registry Map + unused `@zula/api` peerDep | Remove |
| `SessionJwtIssuer` interface | Keep |
| Dead `OAuthSettings.kt` | Leave |
| Web action-group / layouts / form / lazy | Keep for future |
| `ProblemDetailsError` unused | Keep |
| Koin `get` / `inject` / `binds` style | OK |
| `DefaultKeysManager` + `KoinComponent` | OK |

---

## Work packages

### WP1 — Kill unused contracts / stub ports

**Do**
- Delete `core/contracts/{BlockResolver,TrustLedgerWriter,SellerActivityWriter}.kt`
- Strip `UserService` implements + Koin `binds` for those ports
- Drop stub methods (`resolveViewerBlock` → false, empty trust/seller TODOs) from `UserService` unless a real caller needs them soon
- Keep profile write API only (see WP2)

**Done when:** no `binds` multi-interface for user; no `core/contracts` package (or empty gone); compile + user tests green.

### WP2 — One profile write

**Do**
- Collapse `putMyProfile` / `patchMyProfile` → single method (e.g. `updateMyProfile`) with explicit null-means-unchanged semantics
- Update `UserProfileWriter` + any docs that claim PUT full-replace
- Do not invent clear-field sentinel unless product needs it later

**Done when:** one write entrypoint; no duplicate bodies.

### WP3 — Flatten JWT keys

**Do**
- Collapse `KeysManager` / `KeysManagers` / `DefaultKeysManager` / dual `JwtKeys`+`JwtKeySet`+`toLegacyJwtKeys` into a small load path: config → key set → sign/verify
- Keep k8s / PEM / JSON / auto-generate behaviors that are actually used
- Keep `KoinComponent` / `get<Json>()` if needed (decision: OK)
- Keep `SessionJwtIssuer` interface + single impl

**Done when:** fewer files; one key model; existing key tests updated/pass.

### WP4 — Dead auth surface

**Do**
- Remove `AuthRepository.extendOAuthSession`, challenge insert/find/consume (+ SQLDelight `extendOAuthSession` / `*AuthChallenge*` if unused elsewhere)
- Remove `ProviderTokenService.withProviderAccess` if unused
- Delete or rewrite `AuthSessionLogicTest` helpers that invent `resolveOAuthSession` not in prod
- Grep for leftover call sites before schema drop

**Done when:** no dead challenge/session-extend APIs; tests only cover real code.

### WP5 — One provider metadata source

**Do**
- Pick single source for `/auth/providers` payload: prefer `OAuthConfig.configuredProviders(authSettings)` (config-driven) **or** provider `info()` — not both
- Make `GoogleAuthProvider` / `AppleAuthProvider` / `DevAuthProvider` not duplicate scopes/client display fields that config already owns
- Routing + OpenAPI stay on the chosen source

**Done when:** one function builds provider list; no drift between `info()` and config.

### WP6 — Remove dead web OAuth client path

**Do**
- Delete `apps/web/src/lib/oauth/popup-sign-in.ts`, `use-web-oauth-sign-in.ts`
- Delete route `apps/web/src/routes/oauth/callback.tsx` (regen `routeTree.gen.ts`)
- Keep `backend-popup-sign-in.ts` + `/oauth/complete` (live login path)
- Confirm no imports left

**Done when:** login still works via backend popup; no `/oauth/callback` route.

### WP7 — Slim `@zula/oauth`

**Do**
- Remove `providers/registry.ts` (`registerProviderDefinition` / `listProviderDefinitions` / mutable Map)
- Drop exports from package index
- Remove unused `@zula/api` peerDependency from `packages/oauth/package.json`
- Keep authorize-url / crypto / parse-response / react hook if still used by native

**Done when:** package builds; native OAuth still works; no dead registry API.

---

## Suggested order

1. WP6 + WP7 (client dead code — low risk)
2. WP1 + WP2 (user/contracts)
3. WP4 (auth dead APIs / SQL)
4. WP5 (provider metadata)
5. WP3 (JWT keys — touchiest)

Commit per WP. Run server tests + web/native typecheck after each.

---

## Out of scope until questions answered

Open questions live in the agent reply / below. Do not start those until decisions land.

---

## Open questions (need answers)

Fill with pick A/B/C… — then add WPs.

### Auth shape
1. **`AuthProvider` strategy vs hardcoded Google/Apple in `AuthService`**
   - A: Delete strategy; `when (provider)` + plain functions
   - B: Keep strategy; stop hardcoding — route all providers through map
   - C: Leave as-is for now

### Fail-loud vs silent
2. **`configureAuthRouting`: missing `SecurityConfig` → silent no routes**
   - A: `error()` / throw at startup if security module expected
   - B: Explicit log.warn + return (still skip, but visible)
   - C: Leave silent

3. **Cookie / Problem helpers `get()` from Koin on `ApplicationCall`**
   - A: Pass `SecurityConfig` / deps as args (explicit)
   - B: Leave Koin `get()` (decision already: Koin OK)
   - C: Only fix call sites that already have config in scope

### JWT / session behavior
4. **`log.info("token: $token")` in JWT plugin**
   - A: Delete immediately (leak)
   - B: Debug-only behind flag, redacted
   - C: Leave

5. **JWT `validate` hits DB (`JwtSessionValidator`) — fail = no principal**
   - A: Keep; document “revoked session ⇒ unauthenticated”
   - B: Separate explicit check after JWT crypto validate; distinct error/log
   - C: Drop DB check from plugin; check in handlers that need it

6. **`GET /auth/session` refreshes + sets cookies when JWT missing**
   - A: Keep (convenient SPA bootstrap)
   - B: Session GET read-only; client must `POST /auth/refresh`
   - C: Keep refresh but only if `?refresh=1` / explicit header

7. **Logout runs `ensureProviderCredentialsValid(..., force=true)` before revoke**
   - A: Logout = revoke local session only; no provider call
   - B: Keep force check (revoke-all on bad provider)
   - C: Soft check, never fail logout

### Trust / URLs / config
8. **`publicBaseUrl` trusts `X-Forwarded-*`; JWT issuer does not**
   - A: Same policy both (prefer config `APP_URL` only; ignore Host for OAuth too)
   - B: Same policy both (trust forwarded behind known proxy)
   - C: Document split; leave

9. **`jwt.kubernetes.autoPull` parsed, never read**
   - A: Wire it (pull only when `enabled && autoPull`)
   - B: Delete config field
   - C: Leave dead config

10. **Plugin install order = side-effect graph**
    - A: Leave (Ktor normal)
    - B: Comment order deps in yaml / one bootstrap function with named steps
    - C: Harden: fail fast if dependency missing (related to Q2)

### Dev / client auth ergonomics
11. **Dev auth: `deviceInfo` = secret email**
    - A: Add real `email` field on `AuthenticateRequest` for `dev`
    - B: Keep hack; document in OpenAPI description
    - C: Remove custom email; fixed `dev@…` only

12. **`@zula/api` mutator module globals + import-order config**
    - A: Leave (works for web cookie / native bearer)
    - B: Explicit `createApiClient(opts)` factory; no module state
    - C: Minimal: web also call `configureApiClient` at boot (symmetric with native)

13. **Web never `setUnauthorizedHandler`; native auto-refresh**
    - A: Add web 401 → refresh cookie path (parity)
    - B: Document asymmetry; web relies on `/auth/session` refresh
    - C: Remove native auto-refresh; both explicit

14. **Native `import "@/lib/api"` side-effect configure**
    - A: Keep
    - B: Call `configureApiClient()` from app entry (visible)
    - C: Fold into AuthProvider mount

15. **Two popup message types (`zula.oauth.complete` vs `zula.oauth.callback`)**
    - A: After WP6 (delete client popup), only `complete` remains — done
    - B: Unify name now even before delete
    - C: N/A if WP6 ships first

### Jobs
16. **JobRunr + empty consumers registered, never enqueued**
    - A: Leave (matches empty feature packs)
    - B: Don’t register consumers until a job exists
    - C: Remove JobRunr until first real job (keep `/jobs/ping`? see Q17)

17. **`GET /jobs/ping` unauthenticated**
    - A: Auth-protect or admin-only
    - B: Dev-profile only
    - C: Delete endpoint
    - D: Leave open

### Correctness leftovers (not in first Q round)
18. **`/auth/session` `email` filled from JWT `username` claim**
    - A: Fix claim → real email (or rename response field to `username`)
    - B: Leave

19. **OAuth `nonce` generated, never verified**
    - A: Store + verify against id_token
    - B: Stop sending nonce
    - C: Leave

20. **Google refresh: any Exception → revoke credentials**
    - A: Revoke only on invalid_grant / explicit OAuth errors
    - B: Fail open on network; revoke on auth errors
    - C: Leave

21. **`storeProviderRefresh` no-op without encryption key; login may still require it**
    - A: Fail startup if require-refresh && no key
    - B: Soften require when key missing
    - C: Leave

22. **`ensureIdentity` ignores scopes; no `user_id` match check**
    - A: Update scopes; reject identity owned by other user
    - B: Leave until multi-link needed

23. **Username create race (check-then-insert)**
    - A: Catch unique violation → retry
    - B: Leave

24. **OIDC JWKS cache never evicts old kids**
    - A: TTL / max-size eviction
    - B: Leave

25. **Native OAuth always uses `webClientId` (ios/android resolved unused)**
    - A: Pick platform client id
    - B: Delete unused ios/android fields from config
    - C: Leave

26. **Ephemeral JWT keys default (`autoGenerateKey`)**
    - A: Default off outside dev
    - B: Leave; document multi-instance footgun
    - C: Persist auto-gen keys to file/secret always

27. **Auth cookies `secure=true` with local `http://` APP_URL**
    - A: `secure = APP_URL is https`
    - B: Leave (browser localhost quirks)
    - C: Separate cookie secure flag in config
