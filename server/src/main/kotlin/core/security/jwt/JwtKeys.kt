package com.zula.core.security.jwt

import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

data class JwtKeys(
    val privateKeyPem: String?,
    val publicKeyPem: String,
) {
    fun canSign(): Boolean = !privateKeyPem.isNullOrBlank()

    fun verificationAlgorithm(): Algorithm =
        Algorithm.RSA256(publicKeyPem.decodePublicKey(), null)

    fun signingAlgorithm(): Algorithm {
        val privateKey = privateKeyPem?.decodePrivateKey()
            ?: error("JWT private key required to sign session tokens")
        return Algorithm.RSA256(publicKeyPem.decodePublicKey(), privateKey)
    }

    fun toKubernetesSecretJson(namespace: String, secretName: String): String {
        if (!canSign()) error("Cannot export Kubernetes secret without a private key")
        val privatePem = privateKeyPem ?: error("Cannot export Kubernetes secret without a private key")
        return Json.encodeToString(
            KubernetesSecretJson(
                metadata = KubernetesSecretMetadata(name = secretName, namespace = namespace),
                stringData = mapOf(
                    "JWT_PRIVATE_KEY_PEM" to privatePem,
                    "JWT_PUBLIC_KEY_PEM" to publicKeyPem,
                    "JWT_AUTO_GENERATE_KEY" to "false",
                ),
            ),
        )
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

private fun Key.toPem(type: String): String {
    val header = if (type == "PUBLIC") "PUBLIC KEY" else "PRIVATE KEY"
    val encoded = Base64.getEncoder().encodeToString(encoded)
    return "-----BEGIN $header-----\n$encoded\n-----END $header-----"
}

@Serializable
private data class KubernetesSecretJson(
    val apiVersion: String = "v1",
    val kind: String = "Secret",
    val metadata: KubernetesSecretMetadata,
    val type: String = "Opaque",
    val stringData: Map<String, String>,
)

@Serializable
private data class KubernetesSecretMetadata(
    val name: String,
    val namespace: String,
)
