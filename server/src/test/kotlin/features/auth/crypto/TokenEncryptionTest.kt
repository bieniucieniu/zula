package com.zula.features.auth.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TokenEncryptionTest {
    @Test
    fun `missing key returns null`() {
        assertNull(TokenEncryption.from(null))
    }

    @Test
    fun `blank key returns null`() {
        assertNull(TokenEncryption.from("   "))
    }

    @Test
    fun `round-trips with configured key`() {
        val encryption = assertNotNull(TokenEncryption.from("test-provider-key"))
        val cipher = encryption.encrypt("refresh-token-value")
        assertEquals("refresh-token-value", encryption.decrypt(cipher))
    }
}
