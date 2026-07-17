package com.zula.core.http

import io.ktor.http.HttpStatusCode

class HttpException(
    message: String,
    val status: HttpStatusCode,
) : Throwable(message)

inline fun badRequest(message: Any): Nothing =
    throw HttpException(message.toString(), HttpStatusCode.BadRequest)

inline fun unauthorized(message: Any): Nothing =
    throw HttpException(message.toString(), HttpStatusCode.Unauthorized)

inline fun conflict(message: Any): Nothing =
    throw HttpException(message.toString(), HttpStatusCode.Conflict)
