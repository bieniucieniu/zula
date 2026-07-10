package com.zula.features.validation

import io.ktor.server.routing.*

fun Route.configureValidationRouting() {
    route("/validation") {
        handle { TODO("PIN/QR sessions, handoff verify") }
    }
}
