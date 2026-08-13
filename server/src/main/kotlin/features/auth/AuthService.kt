package com.zula.features.auth

import com.zula.Database
import com.zula.core.http.badRequest
import com.zula.core.http.conflict
import com.zula.core.http.unauthorized
import com.zula.core.security.SecurityConfig
import com.zula.core.security.jwt.SessionJwtIssuer
import com.zula.features.auth.crypto.RefreshTokenGenerator
import com.zula.features.auth.domain.*
import com.zula.features.auth.persistence.AuthRepository
import com.zula.features.auth.provider.AuthProviders
import com.zula.features.auth.provider.GoogleOAuthClient
import com.zula.lib.id.Ids
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.postgresql.util.PSQLException
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import kotlin.uuid.Uuid

data class AuthSessionContext(
    val issuer: String,
    val clientIp: String?,
)

data class GoogleOAuthStart(
    val authorizeUrl: String,
    val state: String,
    val nonce: String,
)

class AuthService(
    val repository: AuthRepository,
    val providers: AuthProviders,
    val jwtIssuer: SessionJwtIssuer,
    val securityConfig: SecurityConfig,
    val authSettings: AuthSettings,
    val providerTokenService: ProviderTokenService,
    val googleOAuthClient: GoogleOAuthClient?,
    val json: Json,
    val db: Database
) {
    private val secureRandom = SecureRandom()

    private fun requireGoogleOAuthClient(): GoogleOAuthClient =
        googleOAuthClient ?: badRequest("Google OAuth code flow is not configured")

    fun startGoogleOAuth(redirectUri: String): GoogleOAuthStart {
        val client = requireGoogleOAuthClient()
        val state = randomOAuthState()
        val nonce = randomOAuthState()
        val authorizeUrl = client.buildAuthorizeUrl(
            redirectUri = redirectUri,
            state = state,
            nonce = nonce,
            promptConsent = true,
        )
        return GoogleOAuthStart(
            authorizeUrl = authorizeUrl,
            state = state,
            nonce = nonce,
        )
    }

    suspend fun completeGoogleOAuthCallback(
        code: String,
        expectedNonce: String,
        redirectUri: String,
        session: AuthSessionContext,
    ): AuthTokensResponse {
        val tokenResponse = requireGoogleOAuthClient().exchangeCode(code, redirectUri)

        if (tokenResponse.error != null || tokenResponse.idToken.isNullOrBlank())
            badRequest(tokenResponse.errorDescription ?: tokenResponse.error ?: "Google token exchange failed")


        return authenticateOAuthTokens(
            session = session,
            provider = "google",
            idToken = tokenResponse.idToken,
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
        providerIdentity: ProviderIdentity,
        status: String,
        scopes: String?,
    ): Uuid {
        val metadataJson =
            providerIdentity.metadata?.let {
                val serializer = JsonObject.serializer()
                json.encodeToString(serializer, it)
            }
        val existingIdentity = repository.findIdentity(providerIdentity.provider, providerIdentity.providerUserId)
        if (existingIdentity?.id == null) {
            return repository.insertIdentity(
                userId = userId,
                provider = providerIdentity.provider,
                providerUserId = providerIdentity.providerUserId,
                email = providerIdentity.email,
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
            email = providerIdentity.email,
            metadata = metadataJson,
            status = existingIdentity.credentials_status,
            scopes = scopes ?: existingIdentity.scopes,
        )
        return existingIdentity.id
    }

    suspend fun authenticate(request: AuthenticateRequest, session: AuthSessionContext): AuthTokensResponse {
        return when {
            request.provider == AuthMethods.DEV && request.code != null -> authenticateDev(request, session)
            request.code != null -> authenticateAuthorizationCode(request, session)
            request.idToken != null -> authenticateOAuthTokens(
                session = session,
                provider = request.provider,
                idToken = request.idToken,
                providerRefreshToken = request.providerRefreshToken,
                deviceInfo = request.deviceInfo,
                scopes = request.scopes,
            )

            else -> badRequest("Invalid credential for provider ${request.provider}")
        }
    }

    private suspend fun authenticateDev(
        request: AuthenticateRequest,
        session: AuthSessionContext,
    ): AuthTokensResponse {
        val provider = providers[request.provider] ?: badRequest("Unknown auth provider: ${request.provider}")
        val identity = provider.verify(request.code ?: badRequest("missing code"))

        val existingUser = repository.findUserByIdentity(identity.provider, identity.providerUserId)
        val userId = existingUser?.id ?: createUserWithUniqueUsername(identity)
        val identityId = ensureIdentity(userId, identity, CredentialsStatus.MISSING, scopes = null)
        return establishSession(session, userId, identityId, AuthMethods.DEV, request.deviceInfo)
    }

    private suspend fun authenticateAuthorizationCode(
        request: AuthenticateRequest,
        session: AuthSessionContext,
    ): AuthTokensResponse {
        val redirectUri = request.redirectUri?.takeIf { it.isNotBlank() }
            ?: badRequest("redirectUri required for authorization code flow")
        val code = request.code.takeIf { it.isNullOrBlank() }
            ?: badRequest("Authorization code required for authorization flow")
        return when (request.provider) {
            "google" -> {
                val tokenResponse = requireGoogleOAuthClient().exchangeCode(code, redirectUri, request.codeVerifier)

                if (tokenResponse.error != null || tokenResponse.idToken.isNullOrBlank())
                    badRequest(tokenResponse.errorDescription ?: tokenResponse.error ?: "Google token exchange failed")

                authenticateOAuthTokens(
                    session = session,
                    provider = request.provider,
                    idToken = tokenResponse.idToken,
                    providerRefreshToken = tokenResponse.refreshToken,
                    deviceInfo = request.deviceInfo,
                    scopes = request.scopes,
                )
            }

            else -> badRequest("Authorization code flow is not supported for [${request.provider}], is only supported for Google")
        }
    }

    private suspend fun authenticateOAuthTokens(
        session: AuthSessionContext,
        provider: String,
        idToken: String,
        providerRefreshToken: String?,
        deviceInfo: String?,
        scopes: String? = null,
        expectedNonce: String? = null,
    ): AuthTokensResponse {
        val authProvider = providers[provider] ?: badRequest("Unknown auth provider: $provider")
        val providerIdentity = authProvider.verify(idToken, providerRefreshToken, scopes, expectedNonce)

        val userId = ensureUserByProvider(providerIdentity)
        val identityId = ensureIdentity(userId, providerIdentity, CredentialsStatus.MISSING, scopes)

        persistProviderRefresh(identityId, providerRefreshToken)

        return establishSession(session, userId, identityId, AuthMethods.amrFor(provider), deviceInfo)
    }

    private fun persistProviderRefresh(identityId: Uuid, providerRefreshToken: String?) {
        if (!providerRefreshToken.isNullOrBlank())
            return providerTokenService.storeProviderRefresh(identityId, providerRefreshToken)

        if (authSettings.requireProviderRefreshOnLogin) badRequest("provider_refresh_required")
    }

    suspend fun refresh(refreshToken: String, session: AuthSessionContext): AuthTokensResponse {
        val hash = RefreshTokenGenerator.hash(refreshToken)
        val now = Instant.now().epochSecond

        // Validate first (no claim / no next session). Provider check must pass before rotate.
        val pending = repository.requireValidRefreshSession(hash, now)
        pending.identity_id?.let { identityId ->
            providerTokenService.ensureProviderCredentialsValid(identityId, force = false)
        }

        val newRefresh = RefreshTokenGenerator.generate()
        val newHash = RefreshTokenGenerator.hash(newRefresh)
        val newExpires = now + securityConfig.jwt.refreshTokenTtlSeconds
        val (oldSession, nextId) = repository.rotateRefreshSession(
            refreshHash = hash,
            newRefreshHash = newHash,
            newExpiresAt = newExpires,
            now = now,
        )

        val user = repository.getUserById(oldSession.user_id)
            ?: unauthorized("User not found")
        val email = resolveEmail(oldSession.user_id, oldSession.identity_id)
        val accessToken = jwtIssuer.issue(
            issuer = session.issuer,
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
        session: AuthSessionContext,
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
            ipAddress = session.clientIp,
            expiresAt = refreshExpires,
            rotatedFromId = null,
        )

        val user = repository.getUserById(userId) ?: unauthorized("User not found")
        val email = resolveEmail(userId, identityId)
        val accessToken = jwtIssuer.issue(
            issuer = session.issuer,
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

    private fun ensureUserByProvider(identity: ProviderIdentity): Uuid {
        return db.transactionWithResult {
            val existingUser = repository.findUserByIdentity(identity.provider, identity.providerUserId)
            existingUser?.id ?: createUserWithUniqueUsername(identity)
        }
    }

    private fun createUserWithUniqueUsername(identity: ProviderIdentity): Uuid {
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

    private fun generateUsername(identity: ProviderIdentity): String {
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
