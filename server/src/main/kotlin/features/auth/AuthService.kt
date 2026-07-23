package com.zula.features.auth

import com.zula.core.http.badRequest
import com.zula.core.http.unauthorized
import com.zula.core.security.JwtConfig
import com.zula.core.security.jwt.SessionJwtIssuer
import com.zula.features.auth.crypto.RefreshTokenGenerator
import com.zula.features.auth.domain.*
import com.zula.features.auth.persistence.AuthRepository
import com.zula.features.auth.provider.AuthProviders
import com.zula.features.auth.provider.EmailOtpAuthProvider
import com.zula.features.auth.provider.MagicLinkAuthProvider
import com.zula.lib.id.Ids
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.uuid.Uuid

class AuthService(
    private val repository: AuthRepository,
    private val providers: AuthProviders,
    private val jwtIssuer: SessionJwtIssuer,
    private val jwtConfig: JwtConfig,
    private val providerTokenService: ProviderTokenService,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun authenticate(call: ApplicationCall, request: AuthenticateRequest): AuthTokensResponse {
        val provider = providers[request.provider]
            ?: badRequest("Unknown auth provider: ${request.provider}")

        val credential = request.toCredential()
        val identity = provider.verify(credential)

        val existingUser = repository.findUserByIdentity(identity.provider, identity.providerUserId)
        val userId = existingUser?.id ?: createUserWithUniqueUsername(identity)

        val metadataJson =
            identity.metadata?.let {
                val serializer = kotlinx.serialization.json.JsonObject.serializer()
                json.encodeToString(serializer, it)
            }
        val existingIdentity = repository.findIdentity(identity.provider, identity.providerUserId)
        if (existingIdentity == null) {
            repository.insertIdentity(
                userId = userId,
                provider = identity.provider,
                providerUserId = identity.providerUserId,
                email = identity.email,
                metadata = metadataJson,
                refreshEnc = null,
                status = if (request.providerRefreshToken != null) CredentialsStatus.ACTIVE else CredentialsStatus.MISSING,
                scopes = request.scopes,
            )
        } else {
            repository.updateIdentityLogin(
                id = existingIdentity.id,
                email = identity.email,
                metadata = metadataJson,
                status = existingIdentity.credentials_status,
            )
        }

        val identityId =
            existingIdentity?.id ?: repository.findIdentity(identity.provider, identity.providerUserId)!!.id
        if (credential is AuthCredential.OAuthIdToken && !credential.providerRefreshToken.isNullOrBlank()) {
            providerTokenService.storeProviderRefresh(identityId, credential.providerRefreshToken)
        }

        val passwordless = AuthMethods.isPasswordless(request.provider)
        val amr = AuthMethods.amrFor(request.provider)
        val now = Instant.now().epochSecond

        val refreshToken = if (passwordless) RefreshTokenGenerator.generate() else null
        val refreshHash = refreshToken?.let(RefreshTokenGenerator::hash)
        val sessionExpires = if (passwordless) {
            now + jwtConfig.refreshTokenTtlSeconds
        } else {
            now + jwtConfig.accessTokenTtlSeconds
        }

        val sessionId = resolveSessionId(
            passwordless = passwordless,
            requestedSessionId = request.sessionId?.let(Ids::parseOrNull),
            userId = userId,
            amr = amr,
            now = now,
            sessionExpires = sessionExpires,
            refreshHash = refreshHash,
            deviceInfo = request.deviceInfo,
            ipAddress = call.request.local.remoteHost,
        )

        val user = repository.getUserById(userId)
            ?: unauthorized("User not found")
        val accessToken = jwtIssuer.issue(
            call = call,
            subject = userId.toString(),
            claims = mapOf(
                "sid" to sessionId.toString(),
                "amr" to amr,
                "username" to user.username,
            ),
        )

        return AuthTokensResponse(
            accessToken = accessToken,
            expiresIn = jwtConfig.accessTokenTtlSeconds,
            refreshToken = refreshToken,
            sessionId = sessionId.toString(),
        )
    }

    suspend fun refresh(call: ApplicationCall, refreshToken: String): AuthTokensResponse {
        val hash = RefreshTokenGenerator.hash(refreshToken)
        val newRefresh = RefreshTokenGenerator.generate()
        val newHash = RefreshTokenGenerator.hash(newRefresh)
        val now = Instant.now().epochSecond
        val newExpires = now + jwtConfig.refreshTokenTtlSeconds

        val (oldSession, nextId) = repository.rotateRefreshSession(
            refreshHash = hash,
            newRefreshHash = newHash,
            newExpiresAt = newExpires,
            now = now,
        )

        val user = repository.getUserById(oldSession.user_id)
            ?: unauthorized("User not found")
        val accessToken = jwtIssuer.issue(
            call = call,
            subject = oldSession.user_id.toString(),
            claims = mapOf(
                "sid" to nextId.toString(),
                "amr" to oldSession.auth_method,
                "username" to user.username,
            ),
        )

        return AuthTokensResponse(
            accessToken = accessToken,
            expiresIn = jwtConfig.accessTokenTtlSeconds,
            refreshToken = newRefresh,
            sessionId = nextId.toString(),
        )
    }

    fun logout(sessionId: Uuid?, refreshToken: String?) {
        when {
            sessionId != null -> repository.revokeSession(sessionId)
            refreshToken != null -> {
                val hash = RefreshTokenGenerator.hash(refreshToken)
                repository.findAnySessionByRefreshHash(hash)?.let { repository.revokeRotationFamily(it.id) }
            }
        }
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

    suspend fun createChallenge(request: ChallengeRequest): ChallengeResponse {
        val now = Instant.now().epochSecond
        val expiresIn = 600L
        val code = EmailOtpAuthProvider.generateCode()
        val hash = EmailOtpAuthProvider.hashCode(request.target, code)
        val challengeId = repository.insertChallenge(
            channel = request.channel,
            target = request.target,
            codeHash = hash,
            purpose = request.purpose,
            expiresAt = now + expiresIn,
        )
        return ChallengeResponse(challengeId = challengeId.toString(), expiresIn = expiresIn, token = code)
    }

    suspend fun createMagicLinkChallenge(channel: String, target: String): ChallengeResponse {
        val token = MagicLinkAuthProvider.generateToken()
        val hash = MagicLinkAuthProvider.hashToken(token)
        val now = Instant.now().epochSecond
        val expiresIn = 900L
        val challengeId = repository.insertChallenge(
            channel = channel,
            target = target,
            codeHash = hash,
            purpose = "magic_link",
            expiresAt = now + expiresIn,
        )
        return ChallengeResponse(challengeId = challengeId.toString(), expiresIn = expiresIn, token = token)
    }

    private fun resolveSessionId(
        passwordless: Boolean,
        requestedSessionId: Uuid?,
        userId: Uuid,
        amr: String,
        now: Long,
        sessionExpires: Long,
        refreshHash: String?,
        deviceInfo: String?,
        ipAddress: String?,
    ): Uuid {
        if (!passwordless && requestedSessionId != null) {
            val owned = repository.findActiveSessionOwnedBy(requestedSessionId, userId, now)
            if (owned != null) {
                repository.extendOAuthSession(owned.id, userId, sessionExpires)
                return owned.id
            }
        }

        val sessionId = Ids.next()
        repository.insertSession(
            userId = userId,
            authMethod = amr,
            refreshHash = refreshHash,
            deviceInfo = deviceInfo,
            ipAddress = ipAddress,
            expiresAt = sessionExpires,
            rotatedFromId = null,
        )
        return sessionId
    }

    private fun AuthenticateRequest.toCredential(): AuthCredential {
        return when {
            AuthMethods.isPasswordless(provider) && code != null && challengeId != null -> {
                val challengeId = Ids.parseOrNull(challengeId)
                    ?: badRequest("Invalid challengeId")
                AuthCredential.EmailOtp(challengeId, code)
            }

            provider == AuthMethods.MAGIC_LINK && magicLinkToken != null ->
                AuthCredential.MagicLink(magicLinkToken)

            provider == AuthMethods.DEV && code != null ->
                AuthCredential.DevBypass(
                    secret = code,
                    email = deviceInfo,
                )

            idToken != null ->
                AuthCredential.OAuthIdToken(
                    idToken = idToken,
                    providerRefreshToken = providerRefreshToken,
                    scopes = scopes,
                )

            else -> badRequest("Invalid credential for provider $provider")
        }
    }

    private fun createUserWithUniqueUsername(identity: Identity): Uuid {
        val base = generateUsername(identity)
        var username = base
        var suffix = 0
        while (repository.findUserByUsername(username) != null) {
            suffix++
            username = "${base.take(32)}_$suffix"
        }

        return repository.createUser(username)
    }

    private fun generateUsername(identity: Identity): String {
        val base = identity.email?.substringBefore('@')
            ?: "${identity.provider}_${identity.providerUserId.take(8)}"
        return base.lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(40)
            .ifBlank { "user_${Ids.next().toHexString().take(12)}" }
    }
}
