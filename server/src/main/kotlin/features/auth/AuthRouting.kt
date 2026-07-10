package com.zula.features.auth

import com.zula.core.security.JwtKeyMaterial
import com.zula.core.security.toJwksResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.configureAuthRouting() {
    val authService by inject<AuthService>()
    val keyMaterial by inject<JwtKeyMaterial>()

    route("/v1/auth") {
        get("/providers") {
            call.respond(authService.listProviders())
        }

        post("/authenticate") {
            val request = call.receive<AuthenticateRequest>()
            val token = authService.authenticateWithIdToken(request.provider, request.idToken)
            call.respond(token.toResponse())
        }

        get("/jwks") {
            call.respond(keyMaterial.toJwksResponse())
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(
                    mapOf(
                        "sub" to principal.subject,
                        "sessionId" to principal.jwtId,
                    ),
                )
            }
        }
    }

    authenticate("auth-oauth-google") {
        get("/login") {
            call.respondRedirect("/callback")
        }

        get("/callback") {
            val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
            val accessToken = principal?.accessToken ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val session = authService.authenticateWithOAuthAccessToken(accessToken)
            val appUrl =
                System.getenv("APP_URL")
                    ?: System.getenv("HOST_BASE_URL")
                    ?: "http://localhost:8080"
            call.respondRedirect("${appUrl.trimEnd('/')}/auth/callback#access_token=${session.accessToken}")
        }
    }
}
