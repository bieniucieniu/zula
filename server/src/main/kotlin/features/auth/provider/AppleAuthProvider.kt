package com.zula.features.auth.provider

import com.zula.core.security.AppleOAuthConfig
import com.zula.core.security.oauth.appleOAuthSettings
import com.zula.features.auth.OAuthProviderInfo
import com.zula.features.auth.domain.AuthCredential
import com.zula.features.auth.domain.Identity
import io.ktor.client.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AppleAuthProvider(
    private val config: AppleOAuthConfig,
    httpClient: HttpClient,
) : AuthProvider {
    override val id: String = "apple"

    private val verifier = OidcIdTokenVerifier(
        httpClient = httpClient,
        jwksUrl = "https://appleid.apple.com/auth/keys",
        issuer = "https://appleid.apple.com",
        audience = config.clientId.orEmpty(),
    )

    override fun info(): OAuthProviderInfo? {
        if (!config.isConfigured) return null
        val settings = appleOAuthSettings(config)
        return OAuthProviderInfo(
            id = id,
            clientId = settings.clientId,
            authorizeUrl = settings.authorizeUrl,
            tokenUrl = settings.accessTokenUrl,
            scopes = settings.defaultScopes,
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
