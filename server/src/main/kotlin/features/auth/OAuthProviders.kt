package com.zula.features.auth

import com.zula.core.security.OAuthConfig
import kotlinx.serialization.Serializable

@Serializable
data class OAuthProvidersResponse(
    val providers: List<OAuthProviderInfo>,
)

@Serializable
data class OAuthProviderInfo(
    val id: String,
    val clientId: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    val scopes: List<String>,
)

fun OAuthConfig.configuredProviders(authSettings: AuthSettings = AuthSettings()): List<OAuthProviderInfo> = buildList {
    google?.let { google ->
        add(
            OAuthProviderInfo(
                id = "google",
                clientId = google.clientId,
                authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                tokenUrl = "https://oauth2.googleapis.com/token",
                scopes = listOf("openid", "email", "profile"),
            ),
        )
    }
    apple?.takeIf { authSettings.appleCodeFlowEnabled }?.let { apple ->
        add(
            OAuthProviderInfo(
                id = "apple",
                clientId = apple.clientId,
                authorizeUrl = "https://appleid.apple.com/auth/authorize",
                tokenUrl = "https://appleid.apple.com/auth/token",
                scopes = listOf("name", "email"),
            ),
        )
    }
}
