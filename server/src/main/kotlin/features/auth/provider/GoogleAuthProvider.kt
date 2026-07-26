package com.zula.features.auth.provider

import com.auth0.jwt.interfaces.DecodedJWT
import com.zula.core.http.unauthorized
import com.zula.core.security.GoogleOAuthConfig
import com.zula.features.auth.OAuthProviderInfo
import com.zula.features.auth.domain.AuthCredential
import com.zula.features.auth.domain.Identity
import io.ktor.client.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GoogleAuthProvider(
    val config: GoogleOAuthConfig,
    httpClient: HttpClient,
    val json: Json,
) : AuthProvider {
    override val id: String = "google"

    val verifier = OidcIdTokenVerifier(
        httpClient = httpClient,
        jwksUrl = "https://www.googleapis.com/oauth2/v3/certs",
        issuers = listOf("https://accounts.google.com", "accounts.google.com"),
        audiences = config.idTokenAudiences,
        json = json
    )

    override fun info(): OAuthProviderInfo = OAuthProviderInfo(
        id = id,
        clientId = config.clientId,
        authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
        tokenUrl = "https://accounts.google.com/o/oauth2/token",
        scopes = listOf("openid", "email", "profile"),
    )

    override suspend fun verify(credential: AuthCredential): Identity {
        val token = credential as? AuthCredential.OAuthIdToken
            ?: error("Google auth requires OAuthIdToken credential")
        val decoded = verifier.verify(token.idToken)
        val expectedNonce = token.expectedNonce
        if (expectedNonce != null) {
            val nonce = decoded.getClaim("nonce").asString()
            if (nonce != expectedNonce) unauthorized("Invalid OAuth nonce")
        }
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
