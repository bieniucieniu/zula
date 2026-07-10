package com.zula.lib.utils

import io.ktor.server.config.*

fun ApplicationConfigValue.toBooleanOrNull(): Boolean? = getString().toBooleanOrNull()

fun Any.toBooleanOrNull(): Boolean? = toString().toBooleanOrNull()

fun String.toBooleanOrNull(): Boolean? {
    return when (this.lowercase()) {
        "false", "0", "no" -> false
        "true", "1", "yes" -> true
        else -> null
    }
}

fun ApplicationConfig.stringOrNull(key: String): String? =
    propertyOrNull(key)?.getString()?.takeIf { it.isNotBlank() }

fun ApplicationConfig.configOrNull(key: String): ApplicationConfig? =
    try {
        config(key)
    } catch (_: Exception) {
        null
    }
