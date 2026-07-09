package com.zula.features.media

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureMediaRouting() {
    routing {
        route("/api/v1/media") {
            // TODO: presigned uploads, object validation
        }
    }
}
