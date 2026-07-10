package com.zula.features.moderation

import io.ktor.server.routing.*

fun Route.configureModerationRouting() {
    route("/moderation") {
        handle {
            TODO("reports, admin actions")
        }
    }
}
