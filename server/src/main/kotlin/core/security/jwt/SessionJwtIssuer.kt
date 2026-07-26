package com.zula.core.security.jwt

import com.auth0.jwt.JWT
import com.zula.core.security.JwtConfig
import com.zula.core.security.normalizeIssuer
import java.time.Instant
import java.util.*

interface SessionJwtIssuer {
    fun issue(issuer: String, subject: String, claims: Map<String, String> = emptyMap()): String
}

class RsaSessionJwtIssuer(
    private val keySet: JwtKeySet,
    private val config: JwtConfig,
) : SessionJwtIssuer {
    override fun issue(issuer: String, subject: String, claims: Map<String, String>): String {
        val signingKey = keySet.getActiveSigningKey()
            ?: error("JWT private key is not configured on this instance")
        if (signingKey.privatePem.isNullOrBlank()) {
            error("JWT private key is not configured on this instance")
        }
        val normalizedIssuer = normalizeIssuer(issuer)
        val expiresAt = Date.from(Instant.now().plusSeconds(config.accessTokenTtlSeconds))
        var builder = JWT.create()
            .withKeyId(signingKey.kid)
            .withIssuer(normalizedIssuer)
            .withAudience(config.audience)
            .withSubject(subject)
            .withIssuedAt(Date())
            .withExpiresAt(expiresAt)
        claims.forEach { (key, value) -> builder = builder.withClaim(key, value) }
        return builder.sign(signingKey.signingAlgorithm())
    }
}
