package com.zula.features.trade

import io.ktor.server.routing.*

fun Route.configureTradeRouting() {
    route("/trades") {
        handle {
            TODO("trade lifecycle, templates")
        }
    }
}
