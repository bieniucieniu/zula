package com.zula.core.security

import com.zula.lib.utils.stringOrNull
import io.ktor.server.config.ApplicationConfig

data class SecurityConfig(
    val appUrl: String?,
    val jwt: JwtConfig,
    val oauth: OAuthConfig,
)

class SecurityConfigBuilder {
    var appUrl: String? = null
    lateinit var jwt: JwtConfig
    lateinit var oauth: OAuthConfig

    fun build(): SecurityConfig = SecurityConfig(appUrl, jwt, oauth)
}

data class JwtConfig(
    val audience: String,
    val realm: String,
    val autoGenerateKey: Boolean,
    val privateKeyPem: String?,
    val publicKeyPem: String?,
    val accessTokenTtlSeconds: Long,
    val kubernetes: JwtKubernetesConfig,
) {
    constructor(config: ApplicationConfig) : this(
        audience = config.stringOrNull("audience") ?: "zula",
        realm = config.stringOrNull("realm") ?: "Zula",
        autoGenerateKey = config.propertyOrNull("autoGenerateKey")?.getString()?.toBooleanStrictOrNull() ?: true,
        privateKeyPem = config.stringOrNull("privateKeyPem"),
        publicKeyPem = config.stringOrNull("publicKeyPem"),
        accessTokenTtlSeconds = config.propertyOrNull("accessTokenTtlSeconds")?.getString()?.toLongOrNull() ?: 3600,
        kubernetes = JwtKubernetesConfig(config.config("kubernetes")),
    )
}

data class JwtKubernetesConfig(
    val enabled: Boolean,
    val namespace: String?,
    val secretName: String,
    val autoPull: Boolean,
    val autoPush: Boolean,
) {
    constructor(config: ApplicationConfig) : this(
        enabled = config.propertyOrNull("enabled")?.getString()?.toBooleanStrictOrNull() ?: false,
        namespace = config.stringOrNull("namespace"),
        secretName = config.stringOrNull("secretName") ?: "zula-jwt-keys",
        autoPull = config.propertyOrNull("autoPull")?.getString()?.toBooleanStrictOrNull() ?: true,
        autoPush = config.propertyOrNull("autoPush")?.getString()?.toBooleanStrictOrNull() ?: false,
    )
}

data class OAuthConfig(
    val google: GoogleOAuthConfig,
    val apple: AppleOAuthConfig,
) {
    constructor(config: ApplicationConfig) : this(
        google = GoogleOAuthConfig(config.config("google")),
        apple = AppleOAuthConfig(config.config("apple")),
    )
}

data class GoogleOAuthConfig(
    val clientId: String?,
    val clientSecret: String?,
) {
    val isConfigured: Boolean
        get() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()

    constructor(config: ApplicationConfig) : this(
        clientId = config.stringOrNull("clientId"),
        clientSecret = config.stringOrNull("clientSecret"),
    )
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

    constructor(config: ApplicationConfig) : this(
        clientId = config.stringOrNull("clientId"),
        teamId = config.stringOrNull("teamId"),
        keyId = config.stringOrNull("keyId"),
        privateKeyPem = config.stringOrNull("privateKeyPem"),
    )
}
