package com.zula.core.security.jwt

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

internal fun String.decodePrivateKey(): RSAPrivateKey {
    val bytes = Base64.getDecoder().decode(stripPemHeaders())
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes)) as RSAPrivateKey
}

internal fun String.decodePublicKey(): RSAPublicKey {
    val bytes = Base64.getDecoder().decode(stripPemHeaders())
    return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes)) as RSAPublicKey
}

internal fun publicPemFromPrivatePem(privateKeyPem: String): String {
    val privateKey = privateKeyPem.decodePrivateKey() as RSAPrivateCrtKey
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(
        RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent),
    ) as RSAPublicKey
    return publicKey.toPem("PUBLIC")
}

internal fun assertKeyPairMatches(privateKeyPem: String, publicKeyPem: String) {
    val derivedPublic = publicPemFromPrivatePem(privateKeyPem).decodePublicKey().encoded
    val providedPublic = publicKeyPem.decodePublicKey().encoded
    require(providedPublic.contentEquals(derivedPublic)) {
        "JWT public key does not match the configured private key"
    }
}

internal fun generateRsa2048Pems(): Pair<String, String> {
    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val privateKey = keyPair.private as RSAPrivateKey
    val publicKey = keyPair.public as RSAPublicKey
    return privateKey.toPem("PRIVATE") to publicKey.toPem("PUBLIC")
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
