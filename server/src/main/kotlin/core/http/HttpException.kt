package com.zula.core.http

import io.ktor.http.HttpStatusCode

/**
 * Client-facing API errors. Throw from services/routes; [configureHttp] StatusPages
 * pattern-matches subtypes into RFC 9457 Problem Details. Do not catch in route
 * handlers when the error should reach the client.
 *
 * [errors] carries form field → error-code lists (see [ProblemDetails.errors]).
 */
sealed class HttpException(
    message: String,
    val status: HttpStatusCode,
    val errors: ProblemDetailsErrors? = null,
) : Throwable(message) {
    class BadRequest(
        message: String,
        errors: ProblemDetailsErrors? = null,
    ) : HttpException(message, HttpStatusCode.BadRequest, errors)

    class Unauthorized(
        message: String,
        errors: ProblemDetailsErrors? = null,
    ) : HttpException(message, HttpStatusCode.Unauthorized, errors)

    class Forbidden(
        message: String,
        errors: ProblemDetailsErrors? = null,
    ) : HttpException(message, HttpStatusCode.Forbidden, errors)

    class NotFound(
        message: String,
        errors: ProblemDetailsErrors? = null,
    ) : HttpException(message, HttpStatusCode.NotFound, errors)

    class Conflict(
        message: String,
        errors: ProblemDetailsErrors? = null,
    ) : HttpException(message, HttpStatusCode.Conflict, errors)
}

fun badRequest(message: Any, errors: ProblemDetailsErrors? = null): Nothing =
    throw HttpException.BadRequest(message.toString(), errors)

fun unauthorized(message: Any, errors: ProblemDetailsErrors? = null): Nothing =
    throw HttpException.Unauthorized(message.toString(), errors)

fun forbidden(message: Any, errors: ProblemDetailsErrors? = null): Nothing =
    throw HttpException.Forbidden(message.toString(), errors)

fun notFound(message: Any, errors: ProblemDetailsErrors? = null): Nothing =
    throw HttpException.NotFound(message.toString(), errors)

fun conflict(message: Any, errors: ProblemDetailsErrors? = null): Nothing =
    throw HttpException.Conflict(message.toString(), errors)
