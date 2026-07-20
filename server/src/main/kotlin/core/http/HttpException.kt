package com.zula.core.http

import io.ktor.http.HttpStatusCode

/**
 * Client-facing API errors. Throw from services/routes; [configureHttp] StatusPages
 * pattern-matches subtypes into RFC 9457 Problem Details. Do not catch in route
 * handlers when the error should reach the client.
 */
sealed class HttpException(
    message: String,
    val status: HttpStatusCode,
) : Throwable(message) {
    class BadRequest(message: String) : HttpException(message, HttpStatusCode.BadRequest)
    class Unauthorized(message: String) : HttpException(message, HttpStatusCode.Unauthorized)
    class Forbidden(message: String) : HttpException(message, HttpStatusCode.Forbidden)
    class NotFound(message: String) : HttpException(message, HttpStatusCode.NotFound)
    class Conflict(message: String) : HttpException(message, HttpStatusCode.Conflict)
}

fun badRequest(message: Any): Nothing =
    throw HttpException.BadRequest(message.toString())

fun unauthorized(message: Any): Nothing =
    throw HttpException.Unauthorized(message.toString())

fun forbidden(message: Any): Nothing =
    throw HttpException.Forbidden(message.toString())

fun notFound(message: Any): Nothing =
    throw HttpException.NotFound(message.toString())

fun conflict(message: Any): Nothing =
    throw HttpException.Conflict(message.toString())
