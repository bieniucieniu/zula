package com.zula.features.auth

import com.zula.core.http.badRequest
import com.zula.core.http.unauthorized
import com.zula.core.security.SecurityConfig
import com.zula.core.security.jwt.SessionJwtIssuer
import com.zula.features.auth.crypto.RefreshTokenGenerator
import com.zula.features.auth.domain.*
import com.zula.features.auth.persistence.AuthRepository
import com.zula.features.auth.provider.AuthProviders
import com.zula.lib.id.Ids
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import kotlin.uuid.Uuid

class AuthService(
    val repository: AuthRepository,
    val providers: AuthProviders,
    val jwtIssuer: SessionJwtIssuer,
    val securityConfig: SecurityConfig,
    val providerTokenService: ProviderTokenService,
    val json: Json
) {

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
        repository.updateIdentityLogin(
            id = existingIdentity.id,
            email = identity.email,
            metadata = metadataJson,
            status = existingIdentity.credentials_status,
        )
        return existingIdentity.id
    }

    suspend fun authenticate(call: ApplicationCall, request: AuthenticateRequest): AuthTokensResponse {
        val provider = providers[request.provider]
            ?: badRequest("Unknown auth provider: ${request.provider}")

        val credential = request.toCredential()
        val identity = provider.verify(credential)

        val existingUser = repository.findUserByIdentity(identity.provider, identity.providerUserId)
        val userId = existingUser?.id ?: createUserWithUniqueUsername(identity)

        val identityId = ensureIdentity(
            userId,
            identity,
            status = if (request.providerRefreshToken != null) CredentialsStatus.ACTIVE else CredentialsStatus.MISSING,
            scopes = request.scopes,
        )

        if (credential is AuthCredential.OAuthIdToken && !credential.providerRefreshToken.isNullOrBlank()) {
            providerTokenService.storeProviderRefresh(identityId, credential.providerRefreshToken)
        }

        val amr = AuthMethods.amrFor(request.provider)
        val now = Instant.now().epochSecond
        val sessionExpires = now + securityConfig.jwt.accessTokenTtlSeconds

        val sessionId = resolveSessionId(
            userId = userId,
            amr = amr,
            sessionExpires = sessionExpires,
            refreshHash = null,
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
            expiresIn = securityConfig.jwt.accessTokenTtlSeconds,
            refreshToken = null,
            sessionId = sessionId.toString(),
        )
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
            expiresIn = securityConfig.jwt.accessTokenTtlSeconds,
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


    private fun resolveSessionId(
        userId: Uuid,
        amr: String,
        sessionExpires: Long,
        refreshHash: String?,
        deviceInfo: String?,
        ipAddress: String?,
    ): Uuid {
        return repository.insertSession(
            userId = userId,
            authMethod = amr,
            refreshHash = refreshHash,
            deviceInfo = deviceInfo,
            ipAddress = ipAddress,
            expiresAt = sessionExpires,
            rotatedFromId = null,
        )
    }

    private fun AuthenticateRequest.toCredential(): AuthCredential {
        return when {
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
