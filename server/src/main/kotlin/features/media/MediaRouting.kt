package com.zula.features.media

import com.zula.features.media.domain.RequestUploadRequest
import com.zula.features.user.authenticateJwt
import com.zula.features.user.requireUserId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import org.koin.ktor.ext.inject

fun Route.configureMediaRouting() {
    val mediaService: MediaService by inject()

    authenticateJwt {
        route("/media") {
            post("/uploads") {
                val body = call.receive<RequestUploadRequest>()
                call.respond(mediaService.requestUpload(call.requireUserId(), body))
            }.describe {
                operationId = "requestUpload"
                tag("media")
            }
        }
    }
}
