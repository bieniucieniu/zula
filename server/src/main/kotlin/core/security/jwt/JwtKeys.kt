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

data class JwtKeys(
    val privateKeyPem: String?,
    val publicKeyPem: String,
) {
    val canSign: Boolean
        get() = !privateKeyPem.isNullOrBlank()

    fun verificationAlgorithm(): Algorithm =
        Algorithm.RSA256(publicKeyPem.decodePublicKey(), null)

    fun signingAlgorithm(): Algorithm {
        val privateKey = privateKeyPem?.decodePrivateKey()
            ?: error("JWT private key required to sign session tokens")
        return Algorithm.RSA256(publicKeyPem.decodePublicKey(), privateKey)
    }

    fun toKubernetesSecretYaml(namespace: String, secretName: String): String {
        require(canSign) { "Cannot export Kubernetes secret without a private key" }
        return buildString {
            appendLine("apiVersion: v1")
            appendLine("kind: Secret")
            appendLine("metadata:")
            appendLine("  name: $secretName")
            appendLine("  namespace: $namespace")
            appendLine("type: Opaque")
            appendLine("stringData:")
            appendLine("  JWT_PRIVATE_KEY_PEM: |")
            privateKeyPem!!.lines().forEach { appendLine("    $it") }
            appendLine("  JWT_PUBLIC_KEY_PEM: |")
            publicKeyPem.lines().forEach { appendLine("    $it") }
            appendLine("  JWT_AUTO_GENERATE_KEY: \"false\"")
        }
    }

    companion object {
        fun fromPrivateKeyPem(privateKeyPem: String): JwtKeys {
            val privateKey = privateKeyPem.decodePrivateKey()
            val crtKey = privateKey as RSAPrivateCrtKey
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                RSAPublicKeySpec(crtKey.modulus, crtKey.publicExponent),
            ) as RSAPublicKey
            return JwtKeys(
                privateKeyPem = privateKeyPem.trim(),
                publicKeyPem = publicKey.toPem("PUBLIC"),
            )
        }

        fun fromKeyPair(privateKeyPem: String, publicKeyPem: String): JwtKeys {
            val derived = fromPrivateKeyPem(privateKeyPem)
            val providedPublic = publicKeyPem.decodePublicKey().encoded
            val derivedPublic = derived.publicKeyPem.decodePublicKey().encoded
            require(providedPublic.contentEquals(derivedPublic)) {
                "JWT public key does not match the configured private key"
            }
            return JwtKeys(
                privateKeyPem = privateKeyPem.trim(),
                publicKeyPem = publicKeyPem.trim(),
            )
        }

        fun verifyOnly(publicKeyPem: String): JwtKeys =
            JwtKeys(privateKeyPem = null, publicKeyPem = publicKeyPem.trim())

        fun generateRsa2048(): JwtKeys {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val privateKey = keyPair.private as RSAPrivateKey
            val publicKey = keyPair.public as RSAPublicKey
            return JwtKeys(
                privateKeyPem = privateKey.toPem("PRIVATE"),
                publicKeyPem = publicKey.toPem("PUBLIC"),
            )
        }
    }
}

object JwtKeyLoader {
    fun load(config: com.zula.core.security.JwtConfig, log: org.slf4j.Logger): JwtKeys {
        val privateKey = config.privateKeyPem
        val publicKey = config.publicKeyPem

        when {
            privateKey != null && publicKey != null -> return JwtKeys.fromKeyPair(privateKey, publicKey)
            privateKey != null -> return JwtKeys.fromPrivateKeyPem(privateKey)
            publicKey != null -> {
                log.info("JWT verify-only mode: public key configured without private key")
                return JwtKeys.verifyOnly(publicKey)
            }
            config.autoGenerateKey -> {
                log.warn("JWT keys not configured; generating ephemeral in-memory RSA key pair")
                return JwtKeys.generateRsa2048()
            }
            else -> error(
                "JWT keys required: set JWT_PRIVATE_KEY_PEM (+ optional JWT_PUBLIC_KEY_PEM), " +
                    "or JWT_PUBLIC_KEY_PEM for verify-only, or enable JWT_AUTO_GENERATE_KEY",
            )
        }
    }
}

internal fun String.decodePrivateKey(): RSAPrivateKey {
    val bytes = Base64.getDecoder().decode(stripPemHeaders())
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes)) as RSAPrivateKey
}

internal fun String.decodePublicKey(): RSAPublicKey {
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
