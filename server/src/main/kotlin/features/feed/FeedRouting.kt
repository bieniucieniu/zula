package com.zula.features.feed

import io.ktor.server.routing.*

fun Route.configureFeedRouting() {
    route("/feed") {
        handle {
            TODO("POST /items, GET /for-you, GET /by-author/{id}")
        }
    }
}
