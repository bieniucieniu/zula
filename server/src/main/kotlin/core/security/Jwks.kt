package com.zula.core.security

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

fun JwtKeyMaterial.toJwksResponse(): JwksResponse =
    JwksResponse(
        keys = listOf(publicKey.toJwk(keyId)),
    )

private fun RSAPublicKey.toJwk(keyId: String): JwkKey {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return JwkKey(
        kty = "RSA",
        use = "sig",
        alg = "RS256",
        kid = keyId,
        n = encoder.encodeToString(modulus.toUnsignedByteArray()),
        e = encoder.encodeToString(publicExponent.toUnsignedByteArray()),
    )
}

private fun BigInteger.toUnsignedByteArray(): ByteArray {
    val bytes = toByteArray()
    return if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
        bytes.copyOfRange(1, bytes.size)
    } else {
        bytes
    }
}
