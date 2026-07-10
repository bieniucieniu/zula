package com.zula.core.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.time.Instant
import java.util.Date
import java.util.UUID

class JwtSessionService(
    private val config: JwtConfig,
    private val keyMaterial: JwtKeyMaterial,
) {
    private val algorithm: Algorithm = Algorithm.RSA256(keyMaterial.publicKey, keyMaterial.privateKey)

    fun issueToken(
        userId: String,
        sessionId: String = UUID.randomUUID().toString(),
    ): IssuedSessionToken {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(config.tokenLifetimeSeconds)
        val token =
            JWT
                .create()
                .withKeyId(keyMaterial.keyId)
                .withIssuer(config.issuer)
                .withAudience(config.audience)
                .withSubject(userId)
                .withJWTId(sessionId)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm)

        return IssuedSessionToken(
            accessToken = token,
            tokenType = "Bearer",
            expiresInSeconds = config.tokenLifetimeSeconds,
            sessionId = sessionId,
        )
    }

    fun verifier(): JWTVerifier =
        JWT
            .require(algorithm)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()
}

data class IssuedSessionToken(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val sessionId: String,
)
