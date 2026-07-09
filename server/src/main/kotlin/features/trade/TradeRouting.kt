package com.zula.features.trade

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureTradeRouting() {
    routing {
        route("/api/v1/trades") {
            // TODO: trade lifecycle, templates
        }
    }
}
