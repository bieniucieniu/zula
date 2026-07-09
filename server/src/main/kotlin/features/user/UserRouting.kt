package com.zula.features.user

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureUserRouting() {
    routing {
        route("/api/v1/users") {
            // TODO: profiles, blocks, portfolio, documents
        }
    }
}
