package com.zula.features.auth.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

data class Identity(
    val provider: String,
    val providerUserId: String,
    val email: String?,
    val metadata: JsonObject? = null,
)

sealed class AuthCredential {
    data class OAuthIdToken(
        val idToken: String,
        val providerRefreshToken: String? = null,
        val scopes: String? = null,
        val expectedNonce: String? = null,
    ) : AuthCredential()

    data class DevBypass(
        val secret: String,
        val email: String?,
    ) : AuthCredential()
}

@Serializable
data class AuthenticateRequest(
    val provider: String,
    val idToken: String? = null,
    val providerRefreshToken: String? = null,
    val scopes: String? = null,
    val code: String? = null,
    val codeVerifier: String? = null,
    val redirectUri: String? = null,
    val deviceInfo: String? = null,
)

@Serializable
data class AuthTokensResponse(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
    val refreshToken: String? = null,
    val sessionId: String,
)

@Serializable
data class SessionResponse(
    val expiresIn: Long,
    /** @nullable */
    val email: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String? = null,
)

@Serializable
data class ChallengeRequest(
    val channel: String,
    val target: String,
    val purpose: String = "login",
)

@Serializable
data class ChallengeResponse(
    val challengeId: String,
    val expiresIn: Long,
    /** One-time OTP or magic-link token until email/SMS delivery is wired. */
    val token: String? = null,
)

@Serializable
data class LinkedProviderResponse(
    val provider: String,
    val providerUserId: String,
    val email: String?,
    val credentialsStatus: String,
)

@Serializable
data class LinkedProvidersResponse(
    val providers: List<LinkedProviderResponse>,
)

object AuthMethods {
    const val OAUTH = "oauth"
    const val DEV = "dev"

    fun amrFor(provider: String): String = when (provider) {
        DEV -> DEV
        else -> OAUTH
    }
}

object CredentialsStatus {
    const val ACTIVE = "active"
    const val REVOKED = "revoked"
    const val MISSING = "missing"
}
