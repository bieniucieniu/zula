package com.zula.core.security

import io.ktor.server.application.*
import io.ktor.server.config.*

data class SecurityConfig(
    val appUrl: String?,
    val jwt: JwtConfig,
    val oauth: OAuthConfig,
) {
    companion object {
        fun from(config: ApplicationConfig): SecurityConfig =
            SecurityConfig(
                appUrl = config.propertyOrNull("security.appUrl")?.getString()?.takeIf { it.isNotBlank() },
                jwt = JwtConfig.from(config.config("security.jwt")),
                oauth = OAuthConfig.from(config.config("security.oauth")),
            )
    }
}

data class JwtConfig(
    val audience: String,
    val realm: String,
    val autoGenerateKey: Boolean,
    val privateKeyPem: String?,
    val publicKeyPem: String?,
    val accessTokenTtlSeconds: Long,
) {
    companion object {
        fun from(config: ApplicationConfig): JwtConfig =
            JwtConfig(
                audience = config.propertyOrNull("audience")?.getString() ?: "zula",
                realm = config.propertyOrNull("realm")?.getString() ?: "Zula",
                autoGenerateKey = config.propertyOrNull("autoGenerateKey")?.getString()?.toBooleanStrictOrNull() ?: true,
                privateKeyPem = config.propertyOrNull("privateKeyPem")?.getString()?.takeIf { it.isNotBlank() },
                publicKeyPem = config.propertyOrNull("publicKeyPem")?.getString()?.takeIf { it.isNotBlank() },
                accessTokenTtlSeconds = config.propertyOrNull("accessTokenTtlSeconds")?.getString()?.toLongOrNull()
                    ?: 3600,
            )
    }
}

data class OAuthConfig(
    val google: GoogleOAuthConfig,
    val apple: AppleOAuthConfig,
) {
    companion object {
        fun from(config: ApplicationConfig): OAuthConfig =
            OAuthConfig(
                google = GoogleOAuthConfig.from(config.config("google")),
                apple = AppleOAuthConfig.from(config.config("apple")),
            )
    }
}

data class GoogleOAuthConfig(
    val clientId: String?,
    val clientSecret: String?,
) {
    val isConfigured: Boolean
        get() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()

    companion object {
        fun from(config: ApplicationConfig): GoogleOAuthConfig =
            GoogleOAuthConfig(
                clientId = config.propertyOrNull("clientId")?.getString()?.takeIf { it.isNotBlank() },
                clientSecret = config.propertyOrNull("clientSecret")?.getString()?.takeIf { it.isNotBlank() },
            )
    }
}

data class AppleOAuthConfig(
    val clientId: String?,
    val teamId: String?,
    val keyId: String?,
    val privateKeyPem: String?,
) {
    val isConfigured: Boolean
        get() = !clientId.isNullOrBlank() &&
            !teamId.isNullOrBlank() &&
            !keyId.isNullOrBlank() &&
            !privateKeyPem.isNullOrBlank()

    companion object {
        fun from(config: ApplicationConfig): AppleOAuthConfig =
            AppleOAuthConfig(
                clientId = config.propertyOrNull("clientId")?.getString()?.takeIf { it.isNotBlank() },
                teamId = config.propertyOrNull("teamId")?.getString()?.takeIf { it.isNotBlank() },
                keyId = config.propertyOrNull("keyId")?.getString()?.takeIf { it.isNotBlank() },
                privateKeyPem = config.propertyOrNull("privateKeyPem")?.getString()?.takeIf { it.isNotBlank() },
            )
    }
}
