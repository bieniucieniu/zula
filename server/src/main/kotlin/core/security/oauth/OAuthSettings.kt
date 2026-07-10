package com.zula.core.security.oauth

import com.zula.core.security.AppleOAuthConfig
import com.zula.core.security.GoogleOAuthConfig
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.OAuthServerSettings

fun googleOAuthSettings(config: GoogleOAuthConfig): OAuthServerSettings.OAuth2ServerSettings =
    OAuthServerSettings.OAuth2ServerSettings(
        name = "google",
        authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
        accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
        requestMethod = HttpMethod.Post,
        clientId = config.clientId.orEmpty(),
        clientSecret = config.clientSecret.orEmpty(),
        defaultScopes = listOf("openid", "email", "profile"),
        extraAuthParameters = listOf("access_type" to "offline", "prompt" to "consent"),
    )

fun appleOAuthSettings(config: AppleOAuthConfig): OAuthServerSettings.OAuth2ServerSettings =
    OAuthServerSettings.OAuth2ServerSettings(
        name = "apple",
        authorizeUrl = "https://appleid.apple.com/auth/authorize",
        accessTokenUrl = "https://appleid.apple.com/auth/token",
        requestMethod = HttpMethod.Post,
        clientId = config.clientId.orEmpty(),
        clientSecret = AppleClientSecret.generate(config),
        defaultScopes = listOf("name", "email"),
        extraAuthParameters = listOf("response_mode" to "query"),
    )
