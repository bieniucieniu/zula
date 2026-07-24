package com.zula.features.auth

import com.zula.User_identities
import com.zula.core.http.HttpException
import com.zula.core.http.badRequest
import com.zula.core.http.conflict
import com.zula.core.http.unauthorized
import com.zula.core.security.SecurityConfig
import com.zula.features.auth.crypto.TokenEncryption
import com.zula.features.auth.domain.CredentialsStatus
import com.zula.features.auth.persistence.AuthRepository
import io.ktor.client.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.uuid.Uuid

class ProviderTokenService(
    val repository: AuthRepository,
    val encryption: TokenEncryption,
    val securityConfig: SecurityConfig,
    val authSettings: AuthSettings,
    val httpClient: HttpClient,
    val json: Json,
) {
    fun storeProviderRefresh(identityId: Uuid, refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) return
        if (!encryption.isConfigured) {
            return
        }
        val enc = encryption.encrypt(refreshToken)
        repository.updateIdentityProviderRefresh(
            id = identityId,
            refreshEnc = enc,
            status = CredentialsStatus.ACTIVE,
            checkedAt = Instant.now().epochSecond,
        )
    }

    fun hasStoredProviderRefresh(identity: User_identities): Boolean =
        !identity.provider_refresh_token_enc.isNullOrBlank()

    suspend fun ensureProviderCredentialsValid(identityId: Uuid, force: Boolean = false) {
        val identity = repository.findIdentityById(identityId) ?: return
        if (identity.provider == "dev") return
        if (!hasStoredProviderRefresh(identity)) return

        val now = Instant.now().epochSecond
        val checkedAt = identity.credentials_checked_at ?: 0
        if (!force && now - checkedAt < authSettings.providerCheckIntervalSeconds) {
            return
        }

        try {
            resolveAccessToken(identity, forceRefresh = true)
        } catch (error: HttpException) {
            if (error.status == HttpStatusCode.Conflict) {
                repository.revokeIdentityCredentials(identity.id, now)
                repository.revokeSessionsForIdentity(identity.id)
                unauthorized("Provider credentials revoked")
            }
            throw error
        } catch (error: Exception) {
            if (isProviderRevoked(error)) {
                repository.revokeIdentityCredentials(identity.id, now)
                repository.revokeSessionsForIdentity(identity.id)
                unauthorized("Provider credentials revoked")
            }
            // Fail open on transient provider/network errors.
        }
    }

    suspend fun ensureProviderCredentialsValidForSession(sessionId: Uuid, force: Boolean = false) {
        val session = repository.findSession(sessionId) ?: unauthorized("Invalid session")
        val identityId = session.identity_id ?: return
        ensureProviderCredentialsValid(identityId, force)
    }

    private suspend fun resolveAccessToken(identity: User_identities, forceRefresh: Boolean = false): String {
        val now = Instant.now().epochSecond
        val cachedEnc = identity.provider_access_token_enc
        val expiresAt = identity.provider_token_expires_at
        if (!forceRefresh && cachedEnc != null && expiresAt != null && expiresAt > now + 60) {
            return encryption.decrypt(cachedEnc)
        }

        val refreshEnc = identity.provider_refresh_token_enc
            ?: badRequest("No provider refresh token stored")
        val refresh = encryption.decrypt(refreshEnc)

        return when (identity.provider) {
            "google" -> refreshGoogleAccess(identity, refresh, now)
            else -> badRequest("Provider token refresh not supported for ${identity.provider}")
        }
    }

    private suspend fun refreshGoogleAccess(identity: User_identities, refresh: String, now: Long): String {
        val body: String = httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refresh)
                append("client_id", securityConfig.oauth.google.clientId.orEmpty())
                append("client_secret", securityConfig.oauth.google.clientSecret.orEmpty())
            },
        ).bodyAsText()

        val response: GoogleTokenResponse = json.decodeFromString(body)

        if (response.error != null) {
            if (response.error == "invalid_grant") {
                repository.revokeIdentityCredentials(identity.id, now)
                throw conflict("Provider credentials revoked: ${identity.provider}")
            }
            badRequest("Provider token refresh failed: ${response.error}")
        }

        val accessToken: String? = response.accessToken
        if (accessToken.isNullOrBlank()) {
            badRequest("Provider token refresh returned no access token")
        }

        val accessEnc = encryption.encrypt(accessToken)
        val expires = now + (response.expiresIn ?: 3600)
        repository.updateIdentityProviderTokens(
            id = identity.id,
            accessEnc = accessEnc,
            expiresAt = expires,
            status = CredentialsStatus.ACTIVE,
            checkedAt = now,
        )
        return accessToken
    }

    private fun isProviderRevoked(error: Exception): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return message.contains("invalid_grant") || message.contains("credentials revoked")
    }
}

@Serializable
private data class GoogleTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val error: String? = null,
)
