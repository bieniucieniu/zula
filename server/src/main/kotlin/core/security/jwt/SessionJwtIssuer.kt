package com.zula.core.security.jwt

import com.auth0.jwt.JWT
import com.zula.core.security.JwtConfig
import com.zula.core.security.jwtIssuer
import io.ktor.server.application.*
import java.time.Instant
import java.util.*

interface SessionJwtIssuer {
    fun issue(call: ApplicationCall, subject: String, claims: Map<String, String> = emptyMap()): String
}

class RsaSessionJwtIssuer(
    private val keySet: JwtKeySet,
    private val config: JwtConfig,
    private val configuredAppUrl: String?,
) : SessionJwtIssuer {
    override fun issue(call: ApplicationCall, subject: String, claims: Map<String, String>): String {
        val signingKey = keySet.getActiveSigningKey()
            ?: error("JWT private key is not configured on this instance")
        if (signingKey.privatePem.isNullOrBlank()) {
            error("JWT private key is not configured on this instance")
        }
        val issuer = jwtIssuer(configuredAppUrl)
            ?: error("security.appUrl / APP_URL is required for JWT issuance")
        val expiresAt = Date.from(Instant.now().plusSeconds(config.accessTokenTtlSeconds))
        var builder = JWT.create()
            .withKeyId(signingKey.kid)
            .withIssuer(issuer)
            .withAudience(config.audience)
            .withSubject(subject)
            .withIssuedAt(Date())
            .withExpiresAt(expiresAt)
        claims.forEach { (key, value) -> builder = builder.withClaim(key, value) }
        return builder.sign(signingKey.signingAlgorithm())
    }
}
