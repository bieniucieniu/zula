package com.zula.core.security

fun interface JwtSessionValidator {
    fun isValid(sessionId: kotlin.uuid.Uuid, userId: kotlin.uuid.Uuid): Boolean
}
