package com.zula.core.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stable machine codes for [ProblemDetails.errors] values (forms / field validation).
 * Wire format is snake_case; do not localize these strings on the server.
 */
@Serializable
enum class ProblemErrorCode {
    @SerialName("required")
    REQUIRED,

    @SerialName("too_short")
    TOO_SHORT,

    @SerialName("too_long")
    TOO_LONG,

    @SerialName("invalid")
    INVALID,

    @SerialName("format")
    FORMAT,

    @SerialName("mismatch")
    MISMATCH,

    @SerialName("taken")
    TAKEN,

    @SerialName("not_found")
    NOT_FOUND,

    @SerialName("expired")
    EXPIRED,

    @SerialName("forbidden")
    FORBIDDEN,

    @SerialName("unauthorized")
    UNAUTHORIZED,

    @SerialName("conflict")
    CONFLICT,
}
