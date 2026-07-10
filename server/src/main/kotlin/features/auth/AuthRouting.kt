package com.zula.features.auth

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.configureAuthRouting() {
    authenticate("auth-oauth-google") {
        get("/login") {
            call.respondRedirect("/callback")
        }

        get("/callback") {
            val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
            call.sessions.set(UserSession(principal?.accessToken.toString()))
            call.respondRedirect("/api/hello")
        }
    }
}
