package com.zula.features.auth.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenEncryptionTest {
    @Test
    fun `missing key is not configured and encrypt fails hard`() {
        val encryption = TokenEncryption(null)
        assertFalse(encryption.isConfigured)
        assertFailsWith<IllegalStateException> {
            encryption.encrypt("secret")
        }
    }

    @Test
    fun `blank key is not configured`() {
        val encryption = TokenEncryption("   ")
        assertFalse(encryption.isConfigured)
    }

    @Test
    fun `round-trips with configured key`() {
        val encryption = TokenEncryption("test-provider-key")
        assertTrue(encryption.isConfigured)
        val cipher = encryption.encrypt("refresh-token-value")
        assertEquals("refresh-token-value", encryption.decrypt(cipher))
    }
}
