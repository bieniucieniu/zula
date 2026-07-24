package com.zula.core.security

import com.zula.lib.utils.configOrNull
import com.zula.lib.utils.stringOrNull
import io.ktor.server.config.*

data class SecurityConfig(
    val appUrl: String?,
    val jwt: JwtConfig,
    val oauth: OAuthConfig,
    val cookies: CookieConfig = CookieConfig(),
    val devAuth: DevAuthConfig?,
)

class SecurityConfigBuilder {
    var appUrl: String? = null
    lateinit var jwt: JwtConfig
    lateinit var oauth: OAuthConfig
    var cookies: CookieConfig = CookieConfig()
    var devAuth: DevAuthConfig? = null

    fun build(): SecurityConfig = SecurityConfig(appUrl, jwt, oauth, cookies, devAuth)
}

data class CookieConfig(
    /** When false, auth/oauth cookies are set without the Secure attribute (local http). */
    val secure: Boolean = true,
) {
    companion object {
        fun from(config: ApplicationConfig?) = CookieConfig(
            secure = config?.propertyOrNull("secure")?.getString()?.toBooleanStrictOrNull() ?: true,
        )
    }
}

data class DevAuthConfig(
    val secret: String,
    val defaultEmail: String = "dev@zula.local",
) {
    companion object {
        fun from(config: ApplicationConfig?): DevAuthConfig? {
            config ?: return null
            return DevAuthConfig(
                secret = config.stringOrNull("secret")?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
                defaultEmail = config.stringOrNull("defaultEmail")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "dev@zula.local",
            )

        }
    }
}

data class JwtConfig(
    val audience: String = "zula",
    val realm: String = "Zula",
    val autoGenerateKey: Boolean = false,
    val keysJson: String? = null,
    val privateKeyPem: String? = null,
    val publicKeyPem: String? = null,
    val signingEnabled: Boolean = true,
    val defaultKeyId: String = "default",
    val accessTokenTtlSeconds: Long = 900,
    val refreshTokenTtlSeconds: Long = 2_592_000,
    val providerTokenEncryptionKey: String? = null,
    val kubernetes: JwtKubernetesConfig = JwtKubernetesConfig(),
) {
    companion object {

        fun from(config: ApplicationConfig?) = JwtConfig(
            audience = config?.stringOrNull("audience") ?: "zula",
            realm = config?.stringOrNull("realm") ?: "Zula",
            autoGenerateKey = config?.propertyOrNull("autoGenerateKey")?.getString()?.toBooleanStrictOrNull() ?: false,
            keysJson = config?.stringOrNull("keysJson"),
            privateKeyPem = config?.stringOrNull("privateKeyPem"),
            publicKeyPem = config?.stringOrNull("publicKeyPem"),
            signingEnabled = config?.propertyOrNull("signingEnabled")?.getString()?.toBooleanStrictOrNull() ?: true,
            defaultKeyId = config?.stringOrNull("defaultKeyId") ?: "default",
            accessTokenTtlSeconds = config?.propertyOrNull("accessTokenTtlSeconds")?.getString()?.toLongOrNull()
                ?: 900,
            refreshTokenTtlSeconds = config?.propertyOrNull("refreshTokenTtlSeconds")?.getString()?.toLongOrNull()
                ?: 2_592_000,
            providerTokenEncryptionKey = config?.stringOrNull("providerTokenEncryptionKey"),
            kubernetes = config?.configOrNull("kubernetes")?.let { JwtKubernetesConfig(it) } ?: JwtKubernetesConfig(),
        )
    }
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
)

data class GoogleOAuthConfig(
    val clientId: String? = null,
    val clientSecret: String? = null,
    val additionalClientIds: List<String> = emptyList(),
) {
    val idTokenAudiences: List<String>
        get() = buildList {
            clientId?.takeIf { it.isNotBlank() }?.let(::add)
            additionalClientIds.filter { it.isNotBlank() }.forEach(::add)
        }.distinct()

    val isIdTokenConfigured: Boolean
        get() = idTokenAudiences.isNotEmpty()

    val isConfigured: Boolean
        get() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()

    companion object {

        fun from(config: ApplicationConfig?) = GoogleOAuthConfig(
            clientId = config?.stringOrNull("clientId")?.trim(),
            clientSecret = config?.stringOrNull("clientSecret")?.trim(),
            additionalClientIds = config?.stringOrNull("additionalClientIds")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
        )
    }
}

data class AppleOAuthConfig(
    val clientId: String? = null,
    val teamId: String? = null,
    val keyId: String? = null,
    val privateKeyPem: String? = null,
    val additionalClientIds: List<String> = emptyList(),
) {
    val idTokenAudiences: List<String>
        get() = buildList {
            clientId?.takeIf { it.isNotBlank() }?.let(::add)
            additionalClientIds.filter { it.isNotBlank() }.forEach(::add)
        }.distinct()

    val isIdTokenConfigured: Boolean
        get() = idTokenAudiences.isNotEmpty()

    val isConfigured: Boolean
        get() = !clientId.isNullOrBlank() &&
                !teamId.isNullOrBlank() &&
                !keyId.isNullOrBlank() &&
                !privateKeyPem.isNullOrBlank()

    companion object {

        fun from(config: ApplicationConfig?) = AppleOAuthConfig(
            clientId = config?.stringOrNull("clientId"),
            teamId = config?.stringOrNull("teamId"),
            keyId = config?.stringOrNull("keyId"),
            privateKeyPem = config?.stringOrNull("privateKeyPem"),
            additionalClientIds = config?.stringOrNull("additionalClientIds")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList(),
        )
    }
}
