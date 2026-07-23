package com.zula.features.auth.provider

import com.zula.core.security.AppleOAuthConfig
import com.zula.features.auth.OAuthProviderInfo
import com.zula.features.auth.domain.AuthCredential
import com.zula.features.auth.domain.Identity
import io.ktor.client.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AppleAuthProvider(
    val config: AppleOAuthConfig,
    httpClient: HttpClient,
    val json: Json
) : AuthProvider {
    override val id: String = "apple"

    private val verifier = OidcIdTokenVerifier(
        httpClient = httpClient,
        jwksUrl = "https://appleid.apple.com/auth/keys",
        issuers = listOf("https://appleid.apple.com"),
        audiences = config.idTokenAudiences,
        json = json
    )

    override fun info(): OAuthProviderInfo? {
        if (!config.isIdTokenConfigured) return null
        return OAuthProviderInfo(
            id = id,
            clientId = config.clientId.orEmpty(),
            authorizeUrl = "https://appleid.apple.com/auth/authorize",
            tokenUrl = "https://appleid.apple.com/auth/token",
            scopes = listOf("name", "email"),
        )
    }

    override suspend fun verify(credential: AuthCredential): Identity {
        val idToken = (credential as? AuthCredential.OAuthIdToken)?.idToken
            ?: error("Apple auth requires OAuthIdToken credential")
        val decoded = verifier.verify(idToken)
        return Identity(
            provider = id,
            providerUserId = decoded.subject,
            email = decoded.getClaim("email").asString(),
            metadata = buildJsonObject {
                decoded.getClaim("email_verified")?.let { put("email_verified", it.asBoolean()) }
            },
        )
    }
}
