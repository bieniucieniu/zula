package com.zula.core.security.jwt

import com.zula.core.security.jwt.JwkSetProvider
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import org.koin.ktor.ext.inject

fun Route.configureJwksRouting() {
    val jwkSetProvider: JwkSetProvider by inject()

    suspend fun ApplicationCall.respondJwks() {
        response.headers.append(HttpHeaders.CacheControl, "public, max-age=3600")
        respond(jwkSetProvider.jwks())
    }

    get("/.well-known/jwks.json") { call.respondJwks() }.describe {
        operationId = "getWellKnownJwks"
        tag("auth")
    }
    get("/auth/jwks") { call.respondJwks() }.describe {
        operationId = "getJwks"
        tag("auth")
    }
}
