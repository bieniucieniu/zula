package com.zula.features.auth

import com.zula.User_identities
import com.zula.core.http.badRequest
import com.zula.core.http.conflict
import com.zula.core.http.unauthorized
import com.zula.core.security.SecurityConfig
import com.zula.core.security.jwt.SessionJwtIssuer
import com.zula.core.security.oauthCallbackUrl
import com.zula.features.auth.crypto.RefreshTokenGenerator
import com.zula.features.auth.domain.*
import com.zula.features.auth.persistence.AuthRepository
import com.zula.features.auth.provider.AuthProviders
import com.zula.features.auth.provider.GoogleOAuthClient
import com.zula.core.security.oauth.OAuthPaths
import com.zula.lib.id.Ids
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.postgresql.util.PSQLException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import kotlin.uuid.Uuid

class AuthService(
    val repository: AuthRepository,
    val providers: AuthProviders,
    val jwtIssuer: SessionJwtIssuer,
    val securityConfig: SecurityConfig,
    val authSettings: AuthSettings,
    val providerTokenService: ProviderTokenService,
    val googleOAuthClient: GoogleOAuthClient?,
    val json: Json,
) {
    private val secureRandom = SecureRandom()

    fun googleCallbackRedirectUri(call: ApplicationCall): String =
        call.oauthCallbackUrl(OAuthPaths.GOOGLE_CALLBACK, securityConfig.appUrl)

    private fun requireGoogleOAuthClient(): GoogleOAuthClient =
        googleOAuthClient ?: badRequest("Google OAuth code flow is not configured")

    fun startGoogleOAuth(call: ApplicationCall, mode: String): String {
        val client = requireGoogleOAuthClient()
        val state = randomOAuthState()
        val nonce = randomOAuthState()
        call.setOAuthStateCookies(state, nonce, mode)
        return client.buildAuthorizeUrl(
            redirectUri = googleCallbackRedirectUri(call),
            state = state,
            nonce = nonce,
            promptConsent = true,
        )
    }

    suspend fun completeGoogleOAuthCallback(call: ApplicationCall, code: String, state: String): AuthTokensResponse {
        val expectedState = call.readOAuthStateCookie() ?: unauthorized("Invalid OAuth state")
        if (state != expectedState) unauthorized("Invalid OAuth state")
        val expectedNonce = call.readOAuthNonceCookie() ?: unauthorized("Invalid OAuth nonce")
        call.clearOAuthStateCookies()

        val tokenResponse = requireGoogleOAuthClient().exchangeCode(
            code = code,
            redirectUri = googleCallbackRedirectUri(call),
        )
        if (tokenResponse.error != null || tokenResponse.idToken.isNullOrBlank()) {
            badRequest(tokenResponse.errorDescription ?: tokenResponse.error ?: "Google token exchange failed")
        }

        return authenticateOAuthTokens(
            call = call,
            provider = "google",
            idToken = tokenResponse.idToken!!,
            providerRefreshToken = tokenResponse.refreshToken,
            deviceInfo = null,
            expectedNonce = expectedNonce,
        )
    }

    suspend fun ensureAuthenticatedSession(sessionId: Uuid, forceProviderCheck: Boolean = false) {
        val session = repository.findSession(sessionId) ?: unauthorized("Invalid session")
        if (session.is_revoked != 0L || session.expires_at < Instant.now().epochSecond) {
            unauthorized("Invalid session")
        }
        providerTokenService.ensureProviderCredentialsValidForSession(sessionId, forceProviderCheck)
    }

    suspend fun ensureIdentity(
        userId: Uuid,
        identity: Identity,
        status: String,
        scopes: String?,
    ): Uuid {
        val metadataJson =
            identity.metadata?.let {
                val serializer = JsonObject.serializer()
                json.encodeToString(serializer, it)
            }
        val existingIdentity = repository.findIdentity(identity.provider, identity.providerUserId)
        if (existingIdentity?.id == null) {
            return repository.insertIdentity(
                userId = userId,
                provider = identity.provider,
                providerUserId = identity.providerUserId,
                email = identity.email,
                metadata = metadataJson,
                refreshEnc = null,
                status = status,
                scopes = scopes,
            )
        }
        if (existingIdentity.user_id != userId) {
            conflict("Identity already linked to another user")
        }
        repository.updateIdentityLogin(
            id = existingIdentity.id,
            email = identity.email,
            metadata = metadataJson,
            status = existingIdentity.credentials_status,
            scopes = scopes ?: existingIdentity.scopes,
        )
        return existingIdentity.id
    }

    suspend fun authenticate(call: ApplicationCall, request: AuthenticateRequest): AuthTokensResponse {
        return when {
            request.provider == AuthMethods.DEV && request.code != null -> authenticateDev(call, request)
            request.code != null -> authenticateAuthorizationCode(call, request)
            request.idToken != null -> authenticateOAuthTokens(
                call = call,
                provider = request.provider,
                idToken = request.idToken,
                providerRefreshToken = request.providerRefreshToken,
                deviceInfo = request.deviceInfo,
                scopes = request.scopes,
            )

            else -> badRequest("Invalid credential for provider ${request.provider}")
        }
    }

    private suspend fun authenticateDev(call: ApplicationCall, request: AuthenticateRequest): AuthTokensResponse {
        val provider = providers[request.provider] ?: badRequest("Unknown auth provider: ${request.provider}")
        val identity = provider.verify(AuthCredential.DevBypass(request.code!!, email = null))
        val existingUser = repository.findUserByIdentity(identity.provider, identity.providerUserId)
        val userId = existingUser?.id ?: createUserWithUniqueUsername(identity)
        val identityId = ensureIdentity(userId, identity, CredentialsStatus.MISSING, scopes = null)
        return establishSession(
            call = call,
            userId = userId,
            identityId = identityId,
            amr = AuthMethods.DEV,
            deviceInfo = request.deviceInfo,
        )
    }

    private suspend fun authenticateAuthorizationCode(
        call: ApplicationCall,
        request: AuthenticateRequest,
    ): AuthTokensResponse {
        if (request.provider != "google") {
            badRequest("Authorization code flow is only supported for Google")
        }
        val redirectUri = request.redirectUri?.takeIf { it.isNotBlank() }
            ?: badRequest("redirectUri required for authorization code flow")
        val tokenResponse = requireGoogleOAuthClient().exchangeCode(
            code = request.code!!,
            redirectUri = redirectUri,
            codeVerifier = request.codeVerifier,
        )
        if (tokenResponse.error != null || tokenResponse.idToken.isNullOrBlank()) {
            badRequest(tokenResponse.errorDescription ?: tokenResponse.error ?: "Google token exchange failed")
        }
        return authenticateOAuthTokens(
            call = call,
            provider = request.provider,
            idToken = tokenResponse.idToken!!,
            providerRefreshToken = tokenResponse.refreshToken,
            deviceInfo = request.deviceInfo,
            scopes = request.scopes,
        )
    }

    private suspend fun authenticateOAuthTokens(
        call: ApplicationCall,
        provider: String,
        idToken: String,
        providerRefreshToken: String?,
        deviceInfo: String?,
        scopes: String? = null,
        expectedNonce: String? = null,
    ): AuthTokensResponse {
        val authProvider = providers[provider] ?: badRequest("Unknown auth provider: $provider")
        val identity = authProvider.verify(
            AuthCredential.OAuthIdToken(
                idToken = idToken,
                providerRefreshToken = providerRefreshToken,
                scopes = scopes,
                expectedNonce = expectedNonce,
            ),
        )

        val existingUser = repository.findUserByIdentity(identity.provider, identity.providerUserId)
        val userId = existingUser?.id ?: createUserWithUniqueUsername(identity)

        val identityId = ensureIdentity(
            userId,
            identity,
            status = CredentialsStatus.MISSING,
            scopes = scopes,
        )
        val identityRow = repository.findIdentityById(identityId)
            ?: badRequest("Identity not found")

        persistProviderRefresh(identityRow, providerRefreshToken)

        return establishSession(
            call = call,
            userId = userId,
            identityId = identityId,
            amr = AuthMethods.amrFor(provider),
            deviceInfo = deviceInfo,
        )
    }

    private fun persistProviderRefresh(identity: User_identities, providerRefreshToken: String?) {
        if (!providerRefreshToken.isNullOrBlank()) {
            providerTokenService.storeProviderRefresh(identity.id, providerRefreshToken)
            return
        }
        if (providerTokenService.hasStoredProviderRefresh(identity)) {
            return
        }
        if (authSettings.requireProviderRefreshOnLogin) {
            badRequest("provider_refresh_required")
        }
    }

    suspend fun refresh(call: ApplicationCall, refreshToken: String): AuthTokensResponse {
        val hash = RefreshTokenGenerator.hash(refreshToken)
        val newRefresh = RefreshTokenGenerator.generate()
        val newHash = RefreshTokenGenerator.hash(newRefresh)
        val now = Instant.now().epochSecond
        val newExpires = now + securityConfig.jwt.refreshTokenTtlSeconds

        val (oldSession, nextId) = repository.rotateRefreshSession(
            refreshHash = hash,
            newRefreshHash = newHash,
            newExpiresAt = newExpires,
            now = now,
        )

        oldSession.identity_id?.let { identityId ->
            providerTokenService.ensureProviderCredentialsValid(identityId, force = false)
        }

        val user = repository.getUserById(oldSession.user_id)
            ?: unauthorized("User not found")
        val email = resolveEmail(oldSession.user_id, oldSession.identity_id)
        val accessToken = jwtIssuer.issue(
            call = call,
            subject = oldSession.user_id.toString(),
            claims = buildMap {
                put("sid", nextId.toString())
                put("amr", oldSession.auth_method)
                put("username", user.username)
                if (email != null) put("email", email)
            },
        )

        return AuthTokensResponse(
            accessToken = accessToken,
            expiresIn = securityConfig.jwt.accessTokenTtlSeconds,
            refreshToken = newRefresh,
            sessionId = nextId.toString(),
        )
    }

    suspend fun logout(sessionId: Uuid?, refreshToken: String?) {
        if (sessionId != null) {
            runCatching {
                providerTokenService.ensureProviderCredentialsValidForSession(sessionId, force = false)
            }
        }
        when {
            sessionId != null -> repository.revokeSession(sessionId)
            refreshToken != null -> {
                val hash = RefreshTokenGenerator.hash(refreshToken)
                repository.findAnySessionByRefreshHash(hash)?.let { repository.revokeRotationFamily(it.id) }
            }
        }
    }

    fun sessionEmail(sessionId: Uuid): String? {
        val session = repository.findSession(sessionId) ?: return null
        return resolveEmail(session.user_id, session.identity_id)
    }

    private fun resolveEmail(userId: Uuid, identityId: Uuid?): String? {
        identityId?.let { id ->
            repository.findIdentityById(id)?.email?.let { return it }
        }
        return repository.listIdentitiesByUser(userId).firstNotNullOfOrNull { it.email }
    }

    fun listLinkedProviders(userId: Uuid): LinkedProvidersResponse {
        val providers = repository.listIdentitiesByUser(userId).map {
            LinkedProviderResponse(
                provider = it.provider,
                providerUserId = it.provider_user_id,
                email = it.email,
                credentialsStatus = it.credentials_status,
            )
        }
        return LinkedProvidersResponse(providers)
    }

    private suspend fun establishSession(
        call: ApplicationCall,
        userId: Uuid,
        identityId: Uuid?,
        amr: String,
        deviceInfo: String?,
    ): AuthTokensResponse {
        val refreshToken = RefreshTokenGenerator.generate()
        val refreshHash = RefreshTokenGenerator.hash(refreshToken)
        val now = Instant.now().epochSecond
        val refreshExpires = now + securityConfig.jwt.refreshTokenTtlSeconds

        val sessionId = repository.insertSession(
            userId = userId,
            identityId = identityId,
            authMethod = amr,
            refreshHash = refreshHash,
            deviceInfo = deviceInfo,
            ipAddress = call.request.local.remoteHost,
            expiresAt = refreshExpires,
            rotatedFromId = null,
        )

        val user = repository.getUserById(userId) ?: unauthorized("User not found")
        val email = resolveEmail(userId, identityId)
        val accessToken = jwtIssuer.issue(
            call = call,
            subject = userId.toString(),
            claims = buildMap {
                put("sid", sessionId.toString())
                put("amr", amr)
                put("username", user.username)
                if (email != null) put("email", email)
            },
        )

        return AuthTokensResponse(
            accessToken = accessToken,
            expiresIn = securityConfig.jwt.accessTokenTtlSeconds,
            refreshToken = refreshToken,
            sessionId = sessionId.toString(),
        )
    }

    private fun createUserWithUniqueUsername(identity: Identity): Uuid {
        val base = generateUsername(identity)
        repeat(8) { attempt ->
            val username = if (attempt == 0) {
                base
            } else {
                "${base.take(32)}_${Ids.next().toHexString().take(6)}"
            }
            try {
                return repository.createUser(username)
            } catch (error: Exception) {
                if (!isUniqueViolation(error)) throw error
            }
        }
        error("Could not allocate unique username")
    }

    private fun isUniqueViolation(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is PSQLException && current.sqlState == "23505") return true
            val message = current.message.orEmpty()
            if (message.contains("unique", ignoreCase = true) && message.contains("username", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun generateUsername(identity: Identity): String {
        val base = identity.email?.substringBefore('@')
            ?: "${identity.provider}_${identity.providerUserId.take(8)}"
        return base.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(40)
            .ifBlank { "user_${Ids.next().toHexString().take(12)}" }
    }

    private fun randomOAuthState(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
