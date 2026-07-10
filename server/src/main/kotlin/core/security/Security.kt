package com.zula.core.security

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin

fun Application.configureSecurity() {
    val jwtSessionService = getKoin().get<JwtSessionService>()
    val jwtConfig = getKoin().get<JwtConfig>()

    authentication {
        jwt("auth-jwt") {
            realm = jwtConfig.realm
            verifier(jwtSessionService.verifier())
            validate { credential ->
                if (credential.payload.audience.contains(jwtConfig.audience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    val appUrl =
        System.getenv("APP_URL")
            ?: System.getenv("HOST_BASE_URL")
            ?: "http://localhost:8080"
    val oauthCallbackUrl = "${appUrl.trimEnd('/')}/api/auth/callback/google"

    authentication {
        oauth("auth-oauth-google") {
            urlProvider = { oauthCallbackUrl }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("GOOGLE_CLIENT_ID") ?: "google-client-id",
                    clientSecret = System.getenv("GOOGLE_CLIENT_SECRET") ?: "google-client-secret",
                    defaultScopes =
                        listOf(
                            "openid",
                            "email",
                            "profile",
                        ),
                )
            }
            client = HttpClient(Apache5)
        }
    }
}

val securityModule = module {
    single { JwtConfig.fromEnvironment() }
    single {
        JwtKeyMaterial.load(get<JwtConfig>().keyId)
    }
    single { JwtSessionService(get(), get()) }
}
