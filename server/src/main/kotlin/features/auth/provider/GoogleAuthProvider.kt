package com.zula.features.auth.provider

import com.auth0.jwt.interfaces.DecodedJWT
import com.zula.core.security.GoogleOAuthConfig
import com.zula.core.security.oauth.googleOAuthSettings
import com.zula.features.auth.OAuthProviderInfo
import com.zula.features.auth.domain.AuthCredential
import com.zula.features.auth.domain.Identity
import io.ktor.client.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GoogleAuthProvider(
    private val config: GoogleOAuthConfig,
    httpClient: HttpClient,
) : AuthProvider {
    override val id: String = "google"

    private val verifier = OidcIdTokenVerifier(
        httpClient = httpClient,
        jwksUrl = "https://www.googleapis.com/oauth2/v3/certs",
        issuers = listOf("https://accounts.google.com", "accounts.google.com"),
        audience = config.clientId.orEmpty(),
    )

    override fun info(): OAuthProviderInfo? {
        if (!config.isConfigured) return null
        val settings = googleOAuthSettings(config)
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
            ?: error("Google auth requires OAuthIdToken credential")
        val decoded = verifier.verify(idToken)
        return Identity(
            provider = id,
            providerUserId = decoded.subject,
            email = decoded.getClaim("email").asString(),
            metadata = decoded.claimsToMetadata(),
        )
    }
}

private fun DecodedJWT.claimsToMetadata(): JsonObject = buildJsonObject {
    getClaim("name").asString()?.let { put("name", it) }
    getClaim("picture").asString()?.let { put("picture", it) }
    getClaim("email_verified")?.let { put("email_verified", it.asBoolean()) }
}
