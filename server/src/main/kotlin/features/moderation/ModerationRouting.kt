package com.zula.features.moderation

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureModerationRouting() {
    routing {
        route("/api/v1/moderation") {
            // TODO: reports, admin actions
        }
    }
}
