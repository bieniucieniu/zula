package com.zula.core.security

import com.zula.lib.utils.configOrNull
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
    val audience: String = "zula",
    val realm: String = "Zula",
    val autoGenerateKey: Boolean = true,
    val privateKeyPem: String? = null,
    val publicKeyPem: String? = null,
    val accessTokenTtlSeconds: Long = 3600,
    val kubernetes: JwtKubernetesConfig = JwtKubernetesConfig(),
) {
    constructor(config: ApplicationConfig) : this(
        audience = config.stringOrNull("audience") ?: "zula",
        realm = config.stringOrNull("realm") ?: "Zula",
        autoGenerateKey = config.propertyOrNull("autoGenerateKey")?.getString()?.toBooleanStrictOrNull() ?: true,
        privateKeyPem = config.stringOrNull("privateKeyPem"),
        publicKeyPem = config.stringOrNull("publicKeyPem"),
        accessTokenTtlSeconds = config.propertyOrNull("accessTokenTtlSeconds")?.getString()?.toLongOrNull() ?: 3600,
        kubernetes = config.configOrNull("kubernetes")?.let { JwtKubernetesConfig(it) } ?: JwtKubernetesConfig(),
    )
}

data class JwtKubernetesConfig(
    val enabled: Boolean = false,
    val namespace: String? = null,
    val secretName: String = "zula-jwt-keys",
    val autoPull: Boolean = true,
    val autoPush: Boolean = false,
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
    val google: GoogleOAuthConfig = GoogleOAuthConfig(),
    val apple: AppleOAuthConfig = AppleOAuthConfig(),
) {
    constructor(config: ApplicationConfig) : this(
        google = config.configOrNull("google")?.let { GoogleOAuthConfig(it) } ?: GoogleOAuthConfig(),
        apple = config.configOrNull("apple")?.let { AppleOAuthConfig(it) } ?: AppleOAuthConfig(),
    )
}

data class GoogleOAuthConfig(
    val clientId: String? = null,
    val clientSecret: String? = null,
) {
    val isConfigured: Boolean
        get() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()

    constructor(config: ApplicationConfig) : this(
        clientId = config.stringOrNull("clientId"),
        clientSecret = config.stringOrNull("clientSecret"),
    )
}

data class AppleOAuthConfig(
    val clientId: String? = null,
    val teamId: String? = null,
    val keyId: String? = null,
    val privateKeyPem: String? = null,
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
