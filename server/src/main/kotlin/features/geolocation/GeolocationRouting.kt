package com.zula.features.geolocation

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureGeolocationRouting() {
    routing {
        route("/api/v1/geolocation") {
            // TODO: fingerprint ingest → location_tag
        }
    }
}
