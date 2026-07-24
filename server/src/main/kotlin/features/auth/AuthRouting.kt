package com.zula.features.auth

import com.ucasoft.ktor.simpleCache.cacheOutput
import com.zula.core.http.badRequest
import com.zula.core.http.unauthorized
import com.zula.core.security.AuthProviderNames
import com.zula.core.security.SecurityConfig
import com.zula.features.auth.domain.AuthenticateRequest
import com.zula.features.auth.domain.RefreshRequest
import com.zula.features.auth.domain.SessionResponse
import com.zula.lib.id.Ids
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.hours

fun Route.configureAuthRouting() {
    val config: SecurityConfig = application.getKoin().getOrNull()
        ?: error("SecurityConfig is required to mount auth routes; ensure security module is installed")
    val authSettings: AuthSettings by inject()
    val authService: AuthService by inject()
    val jwtConfig = config.jwt

    cacheOutput(1.hours) {
        get("/auth/providers") {
            call.respond(OAuthProvidersResponse(config.oauth.configuredProviders(authSettings)))
        }.describe {
            operationId = "listProviders"
            tag("auth")
        }
    }

    get("/auth/oauth/google/start") {
        val mode = call.request.queryParameters["mode"] ?: "redirect"
        val authorizeUrl = authService.startGoogleOAuth(call, mode)
        call.respondRedirect(authorizeUrl)
    }.describe {
        operationId = "startGoogleOAuth"
        tag("auth")
    }

    get("/auth/callback/google") {
        val code = call.request.queryParameters["code"] ?: badRequest("code required")
        val state = call.request.queryParameters["state"] ?: badRequest("state required")
        val error = call.request.queryParameters["error"]
        if (error != null) {
            val mode = call.readOAuthModeCookie()
            call.clearOAuthStateCookies()
            if (mode == "popup") {
                call.respondOAuthPopupResult(success = false, error = error)
            } else {
                call.respondRedirect("/oauth/complete?success=0&error=${error.encodeURLParameter()}")
            }
            return@get
        }

        val tokens = authService.completeGoogleOAuthCallback(call, code, state)
        call.setAccessCookies(tokens)
        val mode = call.readOAuthModeCookie()
        call.clearOAuthStateCookies()
        if (mode == "popup") {
            call.respondOAuthPopupResult(success = true)
        } else {
            call.respondRedirect("/oauth/complete?success=1")
        }
    }.describe {
        operationId = "googleOAuthCallback"
        tag("auth")
    }

    post("/auth/authenticate") {
        val request: AuthenticateRequest = call.receive()
        val tokens = authService.authenticate(call, request)

        call.setAccessCookies(tokens)

        call.respond(tokens)
    }.describe {
        operationId = "authenticate"
        tag("auth")
    }

    post("/auth/refresh") {
        val refreshBody: RefreshRequest? = runCatching {
            call.receive<RefreshRequest>()
        }.getOrNull()
        val refreshToken = refreshBody?.refreshToken ?: call.readRefreshCookie()
        ?: badRequest("refreshToken required")
        val tokens = authService.refresh(call, refreshToken)
        call.setAccessCookies(tokens)

        call.respond(tokens)
    }.describe {
        operationId = "refresh"
        tag("auth")
    }

    authenticate(AuthProviderNames.JWT, optional = true) {
        get("/auth/session") {
            val principal: JWTPrincipal? = call.principal()
            if (principal != null) {
                val sessionId = principal.payload.getClaim("sid").asString()?.let(Ids::parseOrNull)
                    ?: unauthorized("Invalid session")
                authService.ensureAuthenticatedSession(sessionId, forceProviderCheck = false)
                call.respond(
                    SessionResponse(
                        expiresIn = jwtConfig.accessTokenTtlSeconds,
                        email = principal.payload.getClaim("username").asString(),
                    ),
                )
                return@get
            }

            val refreshToken = call.readRefreshCookie() ?: unauthorized("Not authenticated")
            val tokens = authService.refresh(call, refreshToken)
            call.setAccessCookies(tokens)
            call.respond(
                SessionResponse(
                    expiresIn = tokens.expiresIn,
                ),
            )
        }.describe {
            operationId = "getSession"
            tag("auth")
        }

        post("/auth/logout") {
            val principal: JWTPrincipal? = call.principal()
            val sessionId = principal?.payload?.getClaim("sid")?.asString()?.let {
                Ids.parseOrNull(it)
            }
            val refreshBody: RefreshRequest? = runCatching {
                call.receive<RefreshRequest>()
            }.getOrNull()
            val refreshToken = refreshBody?.refreshToken
                ?: call.readRefreshCookie()
            authService.logout(sessionId, refreshToken)
            call.clearAuthCookies()
            call.respond(HttpStatusCode.NoContent)
        }.describe {
            operationId = "logout"
            tag("auth")
        }
    }

    authenticate(AuthProviderNames.JWT) {
        get("/auth/providers/linked") {
            val principal: JWTPrincipal? = call.principal()
            val userId = principal?.payload?.subject?.let(Ids::parseOrNull)
                ?: unauthorized("Invalid subject")
            call.respond(authService.listLinkedProviders(userId))
        }.describe {
            operationId = "listLinkedProviders"
            tag("auth")
        }
    }
}
