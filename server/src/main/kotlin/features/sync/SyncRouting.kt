package com.zula.features.sync

import com.zula.core.http.unauthorized
import com.zula.core.security.AuthProviderNames
import com.zula.features.sync.domain.SyncBatchRequest
import com.zula.lib.id.Ids
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import org.koin.ktor.ext.inject

fun Route.configureSyncRouting() {
    val syncService: SyncService by inject()

    authenticate(AuthProviderNames.JWT) {
        route("/sync") {
            post("/batch") {
                val principal: JWTPrincipal = call.principal()
                    ?: unauthorized("Authentication required")
                val userId = principal.payload.subject?.let(Ids::parseOrNull)
                    ?: unauthorized("Invalid subject")
                val body = call.receive<SyncBatchRequest>()
                call.respond(syncService.applyBatch(userId, body))
            }.describe {
                operationId = "syncBatch"
                tag("sync")
            }
        }
    }
}
