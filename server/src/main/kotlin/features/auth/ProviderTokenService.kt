package com.zula.features.auth

import com.zula.User_identities
import com.zula.core.http.badRequest
import com.zula.core.http.conflict
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
    val httpClient: HttpClient,
    val json: Json
) {

    suspend fun <T> withProviderAccess(userId: Uuid, provider: String, block: suspend (String) -> T): T {
        val identity = repository.findIdentityByUserAndProvider(userId, provider)
            ?: badRequest("Provider $provider not linked")
        if (identity.credentials_status == CredentialsStatus.REVOKED) {
            conflict("Provider credentials revoked: $provider")
        }
        val access = resolveAccessToken(identity)
        return block(access)
    }

    fun storeProviderRefresh(identityId: Uuid, refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) return
        if (!encryption.isConfigured) {
            // Sign-in still succeeds; provider API calls need the key later.
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

    private suspend fun resolveAccessToken(identity: User_identities): String {
        val now = Instant.now().epochSecond
        val cachedEnc = identity.provider_access_token_enc
        val expiresAt = identity.provider_token_expires_at
        if (cachedEnc != null && expiresAt != null && expiresAt > now + 60) {
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
        val response: GoogleTokenResponse = runCatching {
            val body: String = httpClient.submitForm(
                url = "https://oauth2.googleapis.com/token",
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refresh)
                    append("client_id", securityConfig.oauth.google.clientId.orEmpty())
                    append("client_secret", securityConfig.oauth.google.clientSecret.orEmpty())
                },
            ).bodyAsText()
            val decoded: GoogleTokenResponse = json.decodeFromString(body)
            decoded
        }.getOrElse { conflict("Provider credentials revoked: ${identity.provider}") }

        val accessToken: String? = response.accessToken
        if (response.error != null || accessToken.isNullOrBlank()) {
            repository.revokeIdentityCredentials(identity.id)
            conflict("Provider credentials revoked: ${identity.provider}")
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
}

@Serializable
private data class GoogleTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val error: String? = null,
)
