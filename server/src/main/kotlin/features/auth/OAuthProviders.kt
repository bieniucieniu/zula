package com.zula.features.auth

import com.zula.core.security.OAuthConfig
import com.zula.core.security.oauth.appleOAuthSettings
import com.zula.core.security.oauth.googleOAuthSettings
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

fun OAuthConfig.configuredProviders(): List<OAuthProviderInfo> = buildList {
    if (google.isConfigured) {
        val settings = googleOAuthSettings(google)
        add(
            OAuthProviderInfo(
                id = settings.name,
                clientId = settings.clientId,
                authorizeUrl = settings.authorizeUrl,
                tokenUrl = settings.accessTokenUrl,
                scopes = settings.defaultScopes,
            ),
        )
    }
    if (apple.isConfigured) {
        val settings = appleOAuthSettings(apple)
        add(
            OAuthProviderInfo(
                id = settings.name,
                clientId = settings.clientId,
                authorizeUrl = settings.authorizeUrl,
                tokenUrl = settings.accessTokenUrl,
                scopes = settings.defaultScopes,
            ),
        )
    }
}
