package com.zula.core.security

import com.zula.core.security.jwt.JwkSetProvider
import com.zula.core.security.jwt.JwtKeySet
import com.zula.core.security.jwt.RsaSessionJwtIssuer
import com.zula.core.security.jwt.SessionJwtIssuer
import com.zula.core.security.jwt.keys.JwtKeySetVerifier
import com.zula.core.security.jwt.keys.loadJwtKeySet
import com.zula.lib.id.Ids
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

fun Application.configureSecurity() {
    val config: SecurityConfig = get()
    val keySetVerifier: JwtKeySetVerifier = get()
    val sessionValidator: JwtSessionValidator = get()

    installJwt(config, keySetVerifier, sessionValidator)
}

fun securityModule(builder: SecurityConfigBuilder.() -> Unit): Module =
    securityModule(SecurityConfigBuilder().apply(builder).build())

fun securityModule(config: SecurityConfig): Module = module {
    val log = LoggerFactory.getLogger("SecurityModule")

    single { config }

    single<JwtKeySet> {
        val json: Json = get()
        loadJwtKeySet(config.jwt, json, log)
    }

    singleOf(::JwkSetProvider)

    single {
        val keySet: JwtKeySet = get()
        JwtKeySetVerifier(keySet, config.jwt)
    }

    single<SessionJwtIssuer> {
        val keySet: JwtKeySet = get()
        RsaSessionJwtIssuer(keySet, config.jwt)
    }

    single {
        HttpClient()
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
                val expectedIssuer = normalizeIssuer(publicBaseUrl(config.appUrl))
                val tokenIssuer = credential.payload.issuer?.let(::normalizeIssuer)
                if (tokenIssuer != expectedIssuer) return@validate null

                val sessionId = credential.payload.getClaim("sid").asString()
                    ?.let(Ids::parseOrNull)
                    ?: return@validate null
                val userId = credential.payload.subject
                    ?.let(Ids::parseOrNull)
                    ?: return@validate null
                if (!sessionValidator.isValid(sessionId, userId)) {
                    this@installJwt.log.info("sessionId: $sessionId userId: $userId")
                    return@validate null
                }
                JWTPrincipal(credential.payload)
            }
        }
    }
}

object AuthProviderNames {
    const val JWT = "auth-jwt"
}

/** Public issuer / base URL: same Forwarded/Host policy as [publicBaseUrl]. */
fun jwtIssuer(configuredAppUrl: String?): String? =
    configuredAppUrl?.takeIf { it.isNotBlank() }?.let(::normalizeIssuer)
