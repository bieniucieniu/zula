package com.zula.core.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get

/**
 * Field-keyed validation errors for forms.
 * Key = field path; values = [ProblemErrorCode] (not localized messages).
 */
typealias ProblemDetailsErrors = Map<String, List<ProblemErrorCode>>

/**
 * RFC 9457 Problem Details for HTTP APIs (`application/problem+json`).
 * [errors] is an extension member for form field codes.
 */
@Serializable
data class ProblemDetails(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val errors: ProblemDetailsErrors? = null,
)

fun problemDetails(
    status: HttpStatusCode,
    detail: String? = null,
    type: String = "about:blank",
    instance: String? = null,
    errors: ProblemDetailsErrors? = null,
) = ProblemDetails(
    type = type,
    title = status.description,
    status = status.value,
    detail = detail,
    instance = instance,
    errors = errors,
)

fun HttpException.toProblemDetails(instance: String? = null) = problemDetails(
    status = status,
    detail = message,
    instance = instance,
    errors = this.errors,
)

suspend fun ApplicationCall.respondProblem(
    status: HttpStatusCode,
    detail: String? = null,
    type: String = "about:blank",
    errors: ProblemDetailsErrors? = null,
) {
    respondProblem(
        problemDetails(
            status = status,
            detail = detail,
            type = type,
            instance = request.path(),
            errors = errors,
        ),
    )
}

suspend fun ApplicationCall.respondProblem(problem: ProblemDetails) {
    val json: Json = get()
    respondText(
        text = json.encodeToString(problem),
        contentType = ContentType.Application.ProblemJson,
        status = HttpStatusCode.fromValue(problem.status),
    )
}
