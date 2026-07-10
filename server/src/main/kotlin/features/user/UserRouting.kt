package com.zula.features.user

import io.ktor.server.routing.*

fun Route.configureUserRouting() {
    route("/users") {
        handle {
            TODO("profiles, blocks, portfolio, documents")
        }
    }
}
