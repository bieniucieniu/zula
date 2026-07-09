package com.zula.features.feed

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureFeedRouting() {
    routing {
        route("/api/v1/feed") {
            // TODO: POST /items, GET /for-you, GET /by-author/{id}
        }
    }
}
