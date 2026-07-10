package com.zula.features.auth

import com.zula.core.security.SecurityBootstrapKey
import com.zula.core.security.oauth.OAuthProviderNames
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.configureAuthRouting() {
    val bootstrap = application.attributes.getOrNull(SecurityBootstrapKey) ?: return

    if (bootstrap.config.oauth.google.isConfigured) {
        authenticate(OAuthProviderNames.GOOGLE) {
            get("/auth/login/google") {
                // Ktor redirects to Google authorize URL automatically.
            }

            get("/auth/callback/google") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
                call.sessions.set(UserSession(principal?.accessToken.toString()))
                call.respondRedirect("/api")
            }
        }
    }

    if (bootstrap.config.oauth.apple.isConfigured) {
        authenticate(OAuthProviderNames.APPLE) {
            get("/auth/login/apple") {
                // Ktor redirects to Apple authorize URL automatically.
            }

            get("/auth/callback/apple") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
                val idToken = principal?.extraParameters?.get("id_token")
                call.sessions.set(UserSession(idToken ?: principal?.accessToken.toString()))
                call.respondRedirect("/api")
            }
        }
    }
}
