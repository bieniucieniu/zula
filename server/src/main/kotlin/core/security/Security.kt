package com.zula.core.security

import com.auth0.jwt.JWT
import com.zula.core.security.jwt.JwtKeys
import com.zula.core.security.jwt.RsaSessionJwtIssuer
import com.zula.core.security.jwt.SessionJwtIssuer
import com.zula.core.security.jwt.keys.KeysManager
import com.zula.core.security.jwt.keys.KeysManagers
import com.zula.core.security.oauth.OAuthPaths
import com.zula.core.security.oauth.OAuthProviderNames
import com.zula.core.security.oauth.appleOAuthSettings
import com.zula.core.security.oauth.googleOAuthSettings
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

fun Application.configureSecurity() {
    val config = get<SecurityConfig>()
    val jwtKeys = get<JwtKeys>()

    installJwt(config, jwtKeys)

    val oauthClient = get<HttpClient>()
    installGoogleOAuth(config, oauthClient)
    installAppleOAuth(config, oauthClient)
}

fun securityModule(builder: () -> SecurityConfig?): Module = module {
    val config = builder() ?: return@module
    val log = LoggerFactory.getLogger("SecurityModule")

    single { config }

    single<KeysManager> {
        KeysManagers.create(config.jwt, log)
    }

    single<JwtKeys> {
        get<KeysManager>().resolve()
    }

    single<SessionJwtIssuer> {
        RsaSessionJwtIssuer(get(), config.jwt, config.appUrl)
    }

    single {
        HttpClient(Apache)
    }
}

private fun Application.installJwt(config: SecurityConfig, jwtKeys: JwtKeys) {
    authentication {
        jwt(AuthProviderNames.JWT) {
            realm = config.jwt.realm
            verifier { _ ->
                JWT.require(jwtKeys.verificationAlgorithm())
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

private fun Application.installGoogleOAuth(config: SecurityConfig, oauthClient: HttpClient) {
    val google = config.oauth.google
    if (!google.isConfigured) {
        log.info("Google OAuth disabled, missing client credentials")
        return
    }

    authentication {
        oauth(OAuthProviderNames.GOOGLE) {
            urlProvider = { oauthCallbackUrl(OAuthPaths.GOOGLE_CALLBACK, config.appUrl) }
            providerLookup = { googleOAuthSettings(google) }
            client = oauthClient
        }
    }
}

private fun Application.installAppleOAuth(config: SecurityConfig, oauthClient: HttpClient) {
    val apple = config.oauth.apple
    if (!apple.isConfigured) {
        log.info("Apple OAuth disabled, missing credentials")
        return
    }

    authentication {
        oauth(OAuthProviderNames.APPLE) {
            urlProvider = { oauthCallbackUrl(OAuthPaths.APPLE_CALLBACK, config.appUrl) }
            providerLookup = { appleOAuthSettings(apple) }
            client = oauthClient
        }
    }
}

object AuthProviderNames {
    const val JWT = "auth-jwt"
}
