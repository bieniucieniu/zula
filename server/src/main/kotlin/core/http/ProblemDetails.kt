package com.zula.core.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * RFC 9457 Problem Details for HTTP APIs (`application/problem+json`).
 */
@Serializable
data class ProblemDetails(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
)

private val problemJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

suspend fun ApplicationCall.respondProblem(
    status: HttpStatusCode,
    detail: String? = null,
    type: String = "about:blank",
) {
    val body = ProblemDetails(
        type = type,
        title = status.description,
        status = status.value,
        detail = detail,
        instance = request.path(),
    )
    respondText(
        text = problemJson.encodeToString(body),
        contentType = ContentType.Application.ProblemJson,
        status = status,
    )
}
