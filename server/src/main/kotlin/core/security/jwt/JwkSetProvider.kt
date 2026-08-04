package com.zula.core.security.jwt

import com.zula.core.security.jwt.decodePublicKey
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey
import java.util.Base64

@Serializable
data class JwksResponse(
    val keys: List<JwkKey>,
)

@Serializable
data class JwkKey(
    val kty: String,
    val use: String,
    val alg: String,
    val kid: String,
    val n: String,
    val e: String,
)

class JwkSetProvider(
    private val keySet: JwtKeySet,
) {
    fun jwks(): JwksResponse = JwksResponse(
        keys = keySet.publicKeys().map { it.toJwk() },
    )
}

private fun JwtKeyEntry.toJwk(): JwkKey {
    val publicKey = publicPem.decodePublicKey() as RSAPublicKey
    return JwkKey(
        kty = "RSA",
        use = "sig",
        alg = "RS256",
        kid = kid,
        n = publicKey.modulus.toBase64Url(),
        e = publicKey.publicExponent.toBase64Url(),
    )
}

private fun BigInteger.toBase64Url(): String {
    val bytes = toByteArray()
    val trimmed = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    return Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed)
}
