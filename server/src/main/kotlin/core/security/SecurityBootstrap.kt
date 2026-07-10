package com.zula.core.security

import com.auth0.jwt.JWT
import com.zula.core.security.jwt.JwtKeyLoader
import com.zula.core.security.jwt.JwtKeyPair
import com.zula.core.security.jwt.RsaSessionJwtIssuer
import com.zula.core.security.jwt.SessionJwtIssuer
import com.zula.core.security.oauth.OAuthPaths
import com.zula.core.security.oauth.OAuthProviderNames
import com.zula.core.security.oauth.appleOAuthSettings
import com.zula.core.security.oauth.googleOAuthSettings
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

data class SecurityBootstrap(
    val config: SecurityConfig,
    val jwtKeys: JwtKeyPair,
    val sessionJwtIssuer: SessionJwtIssuer,
    val oauthClient: HttpClient,
) {
    fun installAuthentication(application: Application) {
        application.installJwt(this)
        application.installGoogleOAuth(this)
        application.installAppleOAuth(this)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SecurityBootstrap::class.java)

        fun load(application: Application): SecurityBootstrap {
            val config = SecurityConfig.from(application.environment.config)
            val jwtKeys = JwtKeyLoader.load(config.jwt, log)
            return SecurityBootstrap(
                config = config,
                jwtKeys = jwtKeys,
                sessionJwtIssuer = RsaSessionJwtIssuer(jwtKeys, config.jwt, config.appUrl),
                oauthClient = HttpClient(Apache),
            )
        }
    }
}

val SecurityBootstrapKey = AttributeKey<SecurityBootstrap>("SecurityBootstrap")

private fun Application.installJwt(bootstrap: SecurityBootstrap) {
    val config = bootstrap.config
    authentication {
        jwt(AuthProviderNames.JWT) {
            realm = config.jwt.realm
            verifier { _ ->
                JWT.require(bootstrap.jwtKeys.signingAlgorithm())
                    .withAudience(config.jwt.audience)
                    .build()
            }
            validate { credential ->
                val expectedIssuer = normalizeIssuer(publicBaseUrl(config.appUrl))
                val tokenIssuer = credential.payload.issuer?.let(::normalizeIssuer)
                if (tokenIssuer == expectedIssuer) JWTPrincipal(credential.payload) else null
            }
        }
    }
}

private fun Application.installGoogleOAuth(bootstrap: SecurityBootstrap) {
    val google = bootstrap.config.oauth.google
    if (!google.isConfigured) {
        log.info("Google OAuth disabled, missing client credentials")
        return
    }

    authentication {
        oauth(OAuthProviderNames.GOOGLE) {
            urlProvider = { oauthCallbackUrl(OAuthPaths.GOOGLE_CALLBACK, bootstrap.config.appUrl) }
            providerLookup = { googleOAuthSettings(google) }
            client = bootstrap.oauthClient
        }
    }
}

private fun Application.installAppleOAuth(bootstrap: SecurityBootstrap) {
    val apple = bootstrap.config.oauth.apple
    if (!apple.isConfigured) {
        log.info("Apple OAuth disabled, missing credentials")
        return
    }

    authentication {
        oauth(OAuthProviderNames.APPLE) {
            urlProvider = { oauthCallbackUrl(OAuthPaths.APPLE_CALLBACK, bootstrap.config.appUrl) }
            providerLookup = { appleOAuthSettings(apple) }
            client = bootstrap.oauthClient
        }
    }
}

object AuthProviderNames {
    const val JWT = "auth-jwt"
}
