package com.zula.core.security.jwt

import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class JwtKeyPair(
    val privateKeyPem: String,
    val publicKeyPem: String,
) {
    fun signingAlgorithm(): Algorithm = Algorithm.RSA256(loadPublicKey(), loadPrivateKey())

    private fun loadPrivateKey(): RSAPrivateKey = privateKeyPem.decodePrivateKey()

    private fun loadPublicKey(): RSAPublicKey = publicKeyPem.decodePublicKey()

    companion object {
        fun fromPrivateKeyPem(privateKeyPem: String): JwtKeyPair {
            val privateKey = privateKeyPem.decodePrivateKey()
            val crtKey = privateKey as RSAPrivateCrtKey
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                RSAPublicKeySpec(crtKey.modulus, crtKey.publicExponent),
            ) as RSAPublicKey
            return JwtKeyPair(
                privateKeyPem = privateKeyPem.trim(),
                publicKeyPem = publicKey.toPem("PUBLIC"),
            )
        }

        fun generateRsa2048(): JwtKeyPair {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val privateKey = keyPair.private as RSAPrivateKey
            val publicKey = keyPair.public as RSAPublicKey
            return JwtKeyPair(
                privateKeyPem = privateKey.toPem("PRIVATE"),
                publicKeyPem = publicKey.toPem("PUBLIC"),
            )
        }
    }
}

object JwtKeyLoader {
    fun load(config: com.zula.core.security.JwtConfig, log: org.slf4j.Logger): JwtKeyPair {
        config.privateKeyPem?.let { return JwtKeyPair.fromPrivateKeyPem(it) }

        if (config.autoGenerateKey) {
            log.warn("JWT private key not configured; generating ephemeral in-memory RSA key pair")
            return JwtKeyPair.generateRsa2048()
        }

        error("JWT private key required: set security.jwt.privateKeyPem / JWT_PRIVATE_KEY_PEM or enable autoGenerateKey")
    }
}

private fun String.decodePrivateKey(): RSAPrivateKey {
    val bytes = Base64.getDecoder().decode(stripPemHeaders())
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes)) as RSAPrivateKey
}

private fun String.decodePublicKey(): RSAPublicKey {
    val bytes = Base64.getDecoder().decode(stripPemHeaders())
    return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes)) as RSAPublicKey
}

private fun String.stripPemHeaders(): String =
    replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
        .replace("\\s".toRegex(), "")

private fun java.security.Key.toPem(type: String): String {
    val header = if (type == "PUBLIC") "PUBLIC KEY" else "PRIVATE KEY"
    val encoded = Base64.getEncoder().encodeToString(encoded)
    return "-----BEGIN $header-----\n$encoded\n-----END $header-----"
}
