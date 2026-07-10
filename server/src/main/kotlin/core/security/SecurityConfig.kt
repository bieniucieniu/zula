package com.zula.core.security

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
)

data class JwtKubernetesConfig(
    val enabled: Boolean,
    val namespace: String?,
    val secretName: String,
    val autoPull: Boolean,
    val autoPush: Boolean,
)

data class OAuthConfig(
    val google: GoogleOAuthConfig,
    val apple: AppleOAuthConfig,
)

data class GoogleOAuthConfig(
    val clientId: String?,
    val clientSecret: String?,
) {
    val isConfigured: Boolean
        get() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()
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
}
