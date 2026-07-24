package com.zula.core.security.jwt.keys

import com.zula.core.security.jwt.JwtKeySet
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadJwtKeySetTest {
    @Test
    fun `generated keys are valid`() {
        val keySet = JwtKeySet.generateRsa2048()
        assertTrue(validateJwtKeySet(keySet, signingEnabled = true).isValid)
    }

    @Test
    fun `mismatched public key is invalid`() {
        val a = JwtKeySet.generateRsa2048(kid = "a")
        val b = JwtKeySet.generateRsa2048(kid = "b")
        val activeA = a.getActiveSigningKey()!!
        val activeB = b.getActiveSigningKey()!!
        val mismatched = JwtKeySet.fromSinglePem(
            publicPem = activeB.publicPem,
            privatePem = activeA.privatePem,
            kid = "bad",
        )
        assertFalse(validateJwtKeySet(mismatched, signingEnabled = true).isValid)
    }
}
