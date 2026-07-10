package com.zula.features.chat

import io.ktor.server.routing.*

fun Route.configureChatRouting() {
    route("/chat") {
        handle {
            TODO("rooms, messages, WS fan-out")
        }
    }
}
