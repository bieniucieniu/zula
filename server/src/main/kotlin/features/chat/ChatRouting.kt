package com.zula.features.chat

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureChatRouting() {
    routing {
        route("/api/v1/chat") {
            // TODO: rooms, messages, WS fan-out
        }
    }
}
