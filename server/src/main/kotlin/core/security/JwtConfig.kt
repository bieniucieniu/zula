package com.zula.core.security

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val realm: String = "zula",
    val tokenLifetimeSeconds: Long = 15 * 60,
    val keyId: String = "zula-1",
) {
    companion object {
        fun fromEnvironment(): JwtConfig {
            val appUrl = System.getenv("APP_URL")
                ?: System.getenv("HOST_BASE_URL")
                ?: "http://localhost:8080"
            return JwtConfig(
                issuer = System.getenv("JWT_ISSUER") ?: appUrl.trimEnd('/'),
                audience = System.getenv("JWT_AUDIENCE") ?: "zula-api",
                realm = System.getenv("JWT_REALM") ?: "zula",
                tokenLifetimeSeconds = System.getenv("JWT_TOKEN_LIFETIME_SECONDS")?.toLongOrNull() ?: 15 * 60,
                keyId = System.getenv("JWT_KEY_ID") ?: "zula-1",
            )
        }
    }
}
