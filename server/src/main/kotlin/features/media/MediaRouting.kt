package com.zula.features.media

import com.zula.core.http.badRequest
import com.zula.core.storage.ObjectStorage
import com.zula.features.user.authenticateJwt
import com.zula.features.user.requireUserId
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.versions
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

private const val MEDIA_CACHE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60

fun Route.configureMediaRouting() {
    val mediaService: MediaService by inject()
    val storage: ObjectStorage by inject()

    route("/media") {
        // Query `key` (not path tailcard) — OpenAPI/Orval reject `{key...}` / `{**}`.
        get("/objects") {
            val key = call.request.queryParameters["key"]?.trim().orEmpty()
            if (key.isEmpty()) badRequest("key query parameter required")
            val (contentType, stored) = mediaService.openObject(key)
            // UUID object keys are immutable; long-lived public cache + ETag for 304.
            call.response.headers.append(
                HttpHeaders.CacheControl,
                "public, max-age=$MEDIA_CACHE_MAX_AGE_SECONDS, immutable",
            )
            val content = object : OutgoingContent.ReadChannelContent() {
                override val contentType: ContentType =
                    contentType?.let { ContentType.parse(it) } ?: ContentType.Application.OctetStream
                override val contentLength: Long? = stored.contentLength
                override val status: HttpStatusCode = HttpStatusCode.OK
                override fun readFrom(): ByteReadChannel = stored.body.toByteReadChannel()
            }
            content.versions = listOf(EntityTagVersion(key))
            try {
                call.respond(content)
            } finally {
                stored.body.close()
            }
        }.describe {
            operationId = "getMediaObject"
            tag("media")
        }

        authenticateJwt {
            post("/uploads") {
                val declaredType = call.request.contentType()?.withoutParameters()?.toString()
                    ?: call.request.header(HttpHeaders.ContentType)?.substringBefore(';')?.trim()
                    ?: badRequest("Content-Type required")
                val max = storage.config.maxUploadBytes
                val channel = call.receiveChannel()
                val packet = channel.readRemaining(max + 1)
                val bytes = packet.readByteArray()
                if (bytes.size.toLong() > max) {
                    badRequest("body must be at most $max bytes")
                }
                call.respond(mediaService.upload(call.requireUserId(), declaredType, bytes))
            }.describe {
                operationId = "uploadMedia"
                tag("media")
            }
        }
    }
}
