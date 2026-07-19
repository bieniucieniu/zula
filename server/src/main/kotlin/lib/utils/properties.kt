package com.zula.lib.utils

import io.ktor.server.config.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

fun ApplicationConfig.stringOrNull(key: String): String? =
    propertyOrNull(key)?.getString()?.takeIf { it.isNotBlank() }

fun ApplicationConfig.configOrNull(key: String): ApplicationConfig? =
    try {
        config(key)
    } catch (_: Exception) {
        null
    }

@OptIn(ExperimentalContracts::class)
fun <R> attempt(block: () -> R): R? {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    return try {
        block()
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalContracts::class)
fun <T, R> T.attempt(block: T.() -> R): R? {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    return try {
        this.block()
    } catch (_: Throwable) {
        null
    }
}
