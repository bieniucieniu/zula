package com.zula.features.auth.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TokenEncryption(
    keyMaterial: String?,
) {
    private val key: ByteArray? = keyMaterial?.takeIf { it.isNotBlank() }?.let { material ->
        MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
    }

    val isConfigured: Boolean get() = key != null

    fun encrypt(plainText: String): String {
        val k = key ?: error("PROVIDER_TOKEN_ENCRYPTION_KEY is required to store provider tokens")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(plainCipherText: String): String {
        val k = key ?: error("PROVIDER_TOKEN_ENCRYPTION_KEY is required to decrypt provider tokens")
        val bytes = Base64.getDecoder().decode(plainCipherText)
        val iv = bytes.copyOfRange(0, 12)
        val payload = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload))
    }
}

object RefreshTokenGenerator {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
