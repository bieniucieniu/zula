package com.zula.features.user

import com.zula.core.http.unauthorized
import com.zula.core.security.AuthProviderNames
import com.zula.lib.id.Ids
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import kotlin.uuid.Uuid

fun ApplicationCall.requireUserId(): Uuid {
    val principal: JWTPrincipal = principal() ?: unauthorized("Authentication required")
    return principal.payload.subject?.let(Ids::parseOrNull)
        ?: unauthorized("Invalid subject")
}

fun ApplicationCall.optionalUserId(): Uuid? {
    val principal: JWTPrincipal? = principal()
    return principal?.payload?.subject?.let(Ids::parseOrNull)
}

fun Route.authenticateJwt(build: Route.() -> Unit): Route =
    authenticate(AuthProviderNames.JWT, build = build)

fun Route.authenticateJwtOptional(build: Route.() -> Unit): Route =
    authenticate(AuthProviderNames.JWT, optional = true, build = build)
