package com.zula.core.security

import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class JwtKeyMaterial(
    val privateKey: RSAPrivateKey,
    val publicKey: RSAPublicKey,
    val keyId: String,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JwtKeyMaterial::class.java)

        fun load(keyId: String): JwtKeyMaterial {
            val privatePem = System.getenv("JWT_PRIVATE_KEY")
            val publicPem = System.getenv("JWT_PUBLIC_KEY")
            if (!privatePem.isNullOrBlank() && !publicPem.isNullOrBlank()) {
                return JwtKeyMaterial(
                    privateKey = parsePrivateKey(privatePem),
                    publicKey = parsePublicKey(publicPem),
                    keyId = keyId,
                )
            }

            logger.warn(
                "JWT_PRIVATE_KEY / JWT_PUBLIC_KEY not set; generating ephemeral RSA key pair for this process",
            )
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            return JwtKeyMaterial(
                privateKey = keyPair.private as RSAPrivateKey,
                publicKey = keyPair.public as RSAPublicKey,
                keyId = keyId,
            )
        }

        private fun parsePrivateKey(pem: String): RSAPrivateKey {
            val decoded = decodePem(pem)
            val keySpec = PKCS8EncodedKeySpec(decoded)
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey
        }

        private fun parsePublicKey(pem: String): RSAPublicKey {
            val decoded = decodePem(pem)
            val keySpec = X509EncodedKeySpec(decoded)
            return KeyFactory.getInstance("RSA").generatePublic(keySpec) as RSAPublicKey
        }

        private fun decodePem(pem: String): ByteArray {
            val normalized = pem
                .replace("\\n", "\n")
                .lines()
                .filter { line ->
                    !line.startsWith("-----BEGIN") && !line.startsWith("-----END")
                }
                .joinToString("")
            return Base64.getDecoder().decode(normalized)
        }
    }
}
