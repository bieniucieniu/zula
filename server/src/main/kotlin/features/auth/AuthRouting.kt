package com.zula.features.auth

import com.ucasoft.ktor.simpleCache.cacheOutput
import com.zula.core.http.badRequest
import com.zula.core.http.unauthorized
import com.zula.core.security.AuthProviderNames
import com.zula.core.security.SecurityConfig
import com.zula.core.security.oauth.OAuthProviderNames
import com.zula.features.auth.domain.*
import com.zula.lib.id.Ids
import io.ktor.http.*
import io.ktor.server.application.*
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
    val config: SecurityConfig = application.getKoin().getOrNull() ?: return
    val authService: AuthService by inject()
    val jwtConfig = config.jwt

    cacheOutput(1.hours) {
        get("/auth/providers") {
            call.respond(OAuthProvidersResponse(config.oauth.configuredProviders()))
        }.describe {
            operationId = "listProviders"
            tag("auth")
        }
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

        get("/auth/powersync/token") {
            val accessToken = call.resolveAccessToken(authService)
            call.respond(PowerSyncTokenResponse(accessToken = accessToken))
        }.describe {
            operationId = "getPowerSyncToken"
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

    post("/auth/challenge") {
        val request: ChallengeRequest = call.receive()
        val response = when (request.purpose) {
            "magic_link" -> authService.createMagicLinkChallenge(request.channel, request.target)
            else -> authService.createChallenge(request)
        }
        call.respond(response)
    }.describe {
        operationId = "createChallenge"
        tag("auth")
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
    fun Route.configureOAuthRoutes(
        providerName: String,
        provider: String,
    ) {
        authenticate(providerName) {
            get("/auth/login/$provider") {
                // Unauthenticated requests are challenged by the OAuth plugin.
            }.describe {
                operationId = "${provider}Login"
                tag("auth")
            }

            get("/auth/callback/$provider") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
                val idToken = principal?.extraParameters?.get("id_token")
                if (idToken != null) {
                    val tokens = authService.authenticate(
                        call,
                        AuthenticateRequest(
                            provider = provider,
                            idToken = idToken,
                            providerRefreshToken = principal.extraParameters["refresh_token"],
                        ),
                    )
                    call.setAccessCookies(tokens)
                }
                call.respondRedirect("/")
            }.describe {
                operationId = "${provider}Callback"
                tag("auth")
            }
        }
    }

    if (config.oauth.google.isConfigured)
        configureOAuthRoutes(OAuthProviderNames.GOOGLE, "google")


    if (config.oauth.apple.isConfigured)
        configureOAuthRoutes(OAuthProviderNames.APPLE, "apple")

}


private suspend fun ApplicationCall.resolveAccessToken(authService: AuthService): String {
    val principal: JWTPrincipal? = principal<JWTPrincipal>()
    if (principal != null) {
        return readAccessCookie() ?: unauthorized("Missing access token")
    }

    val refreshToken = readRefreshCookie() ?: unauthorized("Not authenticated")
    val tokens = authService.refresh(this, refreshToken)
    setAccessCookies(tokens)
    return tokens.accessToken
}

