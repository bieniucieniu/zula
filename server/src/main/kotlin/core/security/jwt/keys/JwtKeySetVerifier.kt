package com.zula.core.security.jwt.keys

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import com.zula.core.security.JwtConfig
import com.zula.core.security.jwt.JwtKeySet

class JwtKeySetVerifier(
    private val keySet: JwtKeySet,
    private val config: JwtConfig,
) {
    fun defaultVerifier(): JWTVerifier = MultiKeyVerifier(keySet, config.audience)
}

private class MultiKeyVerifier(
    private val keySet: JwtKeySet,
    private val audience: String,
) : JWTVerifier {
    override fun verify(token: String): DecodedJWT = verify(JWT.decode(token))

    override fun verify(decodedJWT: DecodedJWT): DecodedJWT {
        val kid = decodedJWT.keyId
            ?: throw JWTVerificationException("JWT missing kid")
        val entry = keySet.verifyKey(kid)
            ?: throw JWTVerificationException("Unknown JWT kid: $kid")
        val verifier = JWT.require(entry.verificationAlgorithm())
            .withAudience(audience)
            .build()
        return verifier.verify(decodedJWT)
    }
}
