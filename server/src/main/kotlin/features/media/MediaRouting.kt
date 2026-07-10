package com.zula.features.media

import io.ktor.server.routing.*

fun Route.configureMediaRouting() {
    route("/media") {
        handle {
            TODO("presigned uploads, object validation")
        }
    }
}
