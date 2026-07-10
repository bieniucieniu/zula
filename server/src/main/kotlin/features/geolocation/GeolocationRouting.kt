package com.zula.features.geolocation

import io.ktor.server.routing.*

fun Route.configureGeolocationRouting() {
    route("/geolocation") {
        handle {
            TODO("fingerprint ingest → location_tag")
        }
    }
}
