package com.zula.features.validation

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureValidationRouting() {
    routing {
        route("/api/v1/validation") {
            // TODO: PIN/QR sessions, handoff verify
        }
    }
}
