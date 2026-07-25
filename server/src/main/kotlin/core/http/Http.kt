package com.zula.core.http

import com.ucasoft.ktor.simpleCache.SimpleCache
import com.ucasoft.ktor.simpleMemoryCache.memoryCache
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.sse.*
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

fun Application.configureHttp() {
    install(CachingHeaders) {
        options { _, outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                else -> null
            }
        }
    }
    install(SimpleCache) {
        memoryCache {
            invalidateAt = 10.seconds
        }
    }
    val logLevel = environment.config.propertyOrNull("logging.level")?.getString()?.toLogLevel()
    if (logLevel != null) {
        install(CallLogging) {
            level = logLevel
            if (logLevel.toInt() <= Level.DEBUG.toInt()) {
                format { call -> call.toTraceLogString() }
            }
        }
        log.info("call logging level: $logLevel")
    }
    install(Compression)
    install(ConditionalHeaders)
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }
    install(ForwardedHeaders)
    install(XForwardedHeaders)
    install(SSE)
    install(StatusPages) {
        exception<Throwable> { call, cause ->

            when (cause) {
                is HttpException -> call.respondProblem(
                    cause.toProblemDetails(instance = call.request.path()),
                )

                is BadRequestException,
                is CannotTransformContentToTypeException,
                is ContentTransformationException,
                    -> call.respondProblem(
                    status = HttpStatusCode.BadRequest,
                    detail = cause.message ?: "Invalid request",
                )

                is IllegalArgumentException -> call.respondProblem(
                    status = HttpStatusCode.BadRequest,
                    detail = cause.message ?: "Invalid request",
                )

                else -> {
                    call.application.log.error("Unhandled error", cause)
                    call.respondProblem(
                        status = HttpStatusCode.InternalServerError,
                        detail = "Internal Server Error",
                    )
                }
            }
        }
    }
}

fun String.toLogLevel(): Level? {
    val s = trim().uppercase()
    if (s == "TRUE") return Level.TRACE
    if (s == "FALSE" || s == "OFF") return null
    return Level.entries.firstOrNull {
        it.toString() == s
    }
}

private val redactedHeaders = setOf(
    HttpHeaders.Authorization,
    HttpHeaders.Cookie,
    HttpHeaders.SetCookie,
)

private fun ApplicationCall.toTraceLogString(): String {
    val status = response.status()?.toString() ?: "Unhandled"
    val headers = request.headers.entries()
        .sortedBy { it.key.lowercase() }
        .joinToString("\n") { (name, values) ->
            val value = if (redactedHeaders.any { it.equals(name, ignoreCase = true) }) {
                "***"
            } else {
                values.joinToString(", ")
            }
            "  $name: $value"
        }
    return buildString {
        append(status)
        append(": ")
        append(request.httpMethod.value)
        append(" - ")
        append(request.path())
        if (request.queryString().isNotEmpty()) {
            append('?')
            append(request.queryString())
        }
        append(" in ")
        append(processingTimeMillis())
        append("ms")
        if (headers.isNotEmpty()) {
            append("\nHeaders:\n")
            append(headers)
        }
    }
}