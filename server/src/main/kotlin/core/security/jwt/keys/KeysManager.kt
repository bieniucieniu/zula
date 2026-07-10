package com.zula.core.security.jwt.keys

import com.zula.core.security.jwt.JwtKeys

sealed interface KeysValidation {
    data object Valid : KeysValidation

    data class Invalid(
        val reasons: List<String>,
    ) : KeysValidation {
        constructor(reason: String) : this(listOf(reason))
    }

    val isValid: Boolean
        get() = this is Valid
}

fun KeysValidation.ensureValid() {
    if (this is KeysValidation.Invalid) {
        error("Invalid JWT keys: ${reasons.joinToString("; ")}")
    }
}

interface KeysManager {
    fun generate(): JwtKeys

    fun validate(keys: JwtKeys): KeysValidation

    fun pullFromKubernetes(): JwtKeys?

    fun pushToKubernetes(keys: JwtKeys)

    fun resolve(): JwtKeys
}
