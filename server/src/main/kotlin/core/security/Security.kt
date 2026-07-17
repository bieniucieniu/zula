package com.zula.core.security

import com.zula.core.security.jwt.JwtKeySet
import com.zula.core.security.jwt.JwkSetProvider
import com.zula.core.security.jwt.RsaSessionJwtIssuer
import com.zula.core.security.jwt.SessionJwtIssuer
import com.zula.core.security.jwt.keys.JwtKeySetVerifier
import com.zula.core.security.jwt.keys.KeysManager
import com.zula.core.security.jwt.keys.KeysManagers
import com.zula.core.security.oauth.OAuthPaths
import com.zula.core.security.oauth.OAuthProviderNames
import com.zula.core.security.oauth.appleOAuthSettings
import com.zula.core.security.oauth.googleOAuthSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.*
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.oauth
import io.ktor.server.request.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

fun Application.configureSecurity() {
    val config: SecurityConfig = get()
    val keySetVerifier: JwtKeySetVerifier = get()
    val oauthClient: HttpClient = get()
    val sessionValidator: JwtSessionValidator = get()

    installJwt(config, keySetVerifier, sessionValidator)
    installGoogleOAuth(config, oauthClient)
    installAppleOAuth(config, oauthClient)
}

fun securityModule(builder: SecurityConfigBuilder.() -> Unit): Module =
    securityModule(SecurityConfigBuilder().apply(builder).build())

fun securityModule(config: SecurityConfig): Module = module {
    val log = LoggerFactory.getLogger("SecurityModule")

    single { config }

    single<KeysManager> {
        KeysManagers.create(config.jwt, log)
    }

    single<JwtKeySet> {
        val keysManager: KeysManager = get()
        keysManager.resolveKeySet()
    }

    single {
        val keySet: JwtKeySet = get()
        JwkSetProvider(keySet)
    }

    single {
        val keySet: JwtKeySet = get()
        JwtKeySetVerifier(keySet, config.jwt)
    }

    single<SessionJwtIssuer> {
        val keySet: JwtKeySet = get()
        RsaSessionJwtIssuer(keySet, config.jwt, config.appUrl)
    }

    single {
        HttpClient(Apache)
    }
}

private fun Application.installJwt(
    config: SecurityConfig,
    keySetVerifier: JwtKeySetVerifier,
    sessionValidator: JwtSessionValidator,
) {
    authentication {
        jwt(AuthProviderNames.JWT) {
            realm = config.jwt.realm
            verifier(keySetVerifier.defaultVerifier())
            authHeader { call ->
                val raw = call.request.header(HttpHeaders.Authorization)
                if (!raw.isNullOrBlank()) {
                    return@authHeader parseAuthorizationHeader(raw)
                }
                call.request.cookies[ACCESS_COOKIE_NAME]?.let { token ->
                    HttpAuthHeader.Single("Bearer", token)
                }
            }
            validate { credential ->
                val expectedIssuer = jwtIssuer(config.appUrl) ?: return@validate null
                val tokenIssuer = credential.payload.issuer?.let(::normalizeIssuer)
                if (tokenIssuer != expectedIssuer) return@validate null

                val sessionId = credential.payload.getClaim("sid").asString()
                    ?.let(com.zula.lib.id.Ids::parseOrNull)
                    ?: return@validate null
                val userId = credential.payload.subject
                    ?.let(com.zula.lib.id.Ids::parseOrNull)
                    ?: return@validate null
                if (!sessionValidator.isValid(sessionId, userId)) return@validate null

                JWTPrincipal(credential.payload)
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

/** Prefer configured APP_URL for iss; never trust Host/X-Forwarded-* for JWT trust. */
fun jwtIssuer(configuredAppUrl: String?): String? =
    configuredAppUrl?.takeIf { it.isNotBlank() }?.let(::normalizeIssuer)
