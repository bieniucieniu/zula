package com.zula.core.security.jwt

import com.auth0.jwt.JWT
import com.zula.core.security.JwtConfig
import com.zula.core.security.normalizeIssuer
import com.zula.core.security.publicBaseUrl
import io.ktor.server.application.ApplicationCall
import java.time.Instant
import java.util.Date

interface SessionJwtIssuer {
    fun issue(call: ApplicationCall, subject: String, claims: Map<String, String> = emptyMap()): String
}

class RsaSessionJwtIssuer(
    private val keys: JwtKeys,
    private val config: JwtConfig,
    private val configuredAppUrl: String?,
) : SessionJwtIssuer {
    override fun issue(call: ApplicationCall, subject: String, claims: Map<String, String>): String {
        require(keys.canSign) { "JWT private key is not configured on this instance" }
        val issuer = normalizeIssuer(call.publicBaseUrl(configuredAppUrl))
        val expiresAt = Date.from(Instant.now().plusSeconds(config.accessTokenTtlSeconds))
        var builder = JWT.create()
            .withIssuer(issuer)
            .withAudience(config.audience)
            .withSubject(subject)
            .withIssuedAt(Date())
            .withExpiresAt(expiresAt)
        claims.forEach { (key, value) -> builder = builder.withClaim(key, value) }
        return builder.sign(keys.signingAlgorithm())
    }
}
