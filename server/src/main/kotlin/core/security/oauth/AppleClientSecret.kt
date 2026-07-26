package com.zula.core.security.oauth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zula.core.security.AppleOAuthConfig
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

object AppleClientSecret {
    fun generate(config: AppleOAuthConfig): String {
        val privateKey = config.privateKeyPem.decodeEcPrivateKey()
        val algorithm = Algorithm.ECDSA256(null, privateKey)
        val now = Instant.now()
        return JWT.create()
            .withKeyId(config.keyId)
            .withIssuer(config.teamId)
            .withSubject(config.clientId)
            .withAudience("https://appleid.apple.com")
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(150 * 24 * 60 * 60)))
            .sign(algorithm)
    }

    private fun String.decodeEcPrivateKey(): ECPrivateKey {
        val bytes = Base64.getDecoder().decode(
            replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), ""),
        )
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes)) as ECPrivateKey
    }
}
