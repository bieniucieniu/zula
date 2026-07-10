package com.zula.core.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.dsl.module
import org.koin.ktor.ext.inject

fun Application.configureSecurity() {
    val c: SecurityConfig by inject()
    authentication {
        jwt {
            val j = c.jwtConfig
            realm = j.jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(c.privateKey))
                    .withAudience(j.jwtAudience)
                    .withIssuer(j.jwtDomain)
                    .build(),
            )
            validate { credential ->
                if (credential.payload.audience.contains(j.jwtAudience)) JWTPrincipal(credential.payload) else null
            }
        }
    }
    authentication {
        val google = c.googleOauthConfig
        if (google != null)
            oauth("auth-oauth-google") {
                urlProvider = { "http://localhost:8080/callback" }
                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "google",
                        authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                        accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                        requestMethod = HttpMethod.Post,
                        clientId = google.clientId,
                        clientSecret = google.clientSecret,
                        defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile"),
                    )
                }
                client = HttpClient()
            }
        val apple = c.appleOauthConfig
        if (apple != null) TODO("apple oauth2 handler")
    }
}


interface SecurityConfig {
    val privateKey: String?
    val publicKey: String?
    val appDomain: String

    val jwtConfig: JwtConfig
    val googleOauthConfig: OauthConfig?
    val appleOauthConfig: OauthConfig?

}

data class SecurityConfigBuilder(
    override var privateKey: String? = null,
    override var publicKey: String? = null,
    override var appDomain: String = "zula.app",
    override var googleOauthConfig: OauthConfig? = null,
    override var appleOauthConfig: OauthConfig? = null,
    override val jwtConfig: JwtConfigBuilder = JwtConfigBuilder(appDomain)
) : SecurityConfig {
    fun setGoogleOauth(
        clientId: String,
        clientSecret: String
    ) {
        googleOauthConfig = OauthConfigBuilder(clientId, clientSecret)
    }

    fun setAppleOauth(
        clientId: String,
        clientSecret: String
    ) {
        appleOauthConfig = OauthConfigBuilder(clientId, clientSecret)
    }

}


interface OauthConfig {
    val clientId: String
    val clientSecret: String
}

class OauthConfigBuilder(
    override val clientId: String,
    override val clientSecret: String
) : OauthConfig

interface JwtConfig {
    var jwtDomain: String
    var jwtRealm: String
    var jwtAudience: String
}

class JwtConfigBuilder(
    override var jwtDomain: String,
    override var jwtRealm: String = "zula-app",
    override var jwtAudience: String = "zula"
) : JwtConfig

fun securityModule(builder: SecurityConfigBuilder.() -> Unit) =
    securityModule(SecurityConfigBuilder().apply(builder).copy())

fun securityModule(config: SecurityConfig = SecurityConfigBuilder()) = module {
    single<SecurityConfig> { config }
}
