package com.zula.core.security.jwt.keys

import com.zula.core.security.jwt.JwtKeys
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultKeysManagerTest {
    private val manager = DefaultKeysManager(
        config = com.zula.core.security.JwtConfig(
            audience = "zula",
            realm = "Zula",
            autoGenerateKey = true,
            privateKeyPem = null,
            publicKeyPem = null,
            accessTokenTtlSeconds = 3600,
            kubernetes = com.zula.core.security.JwtKubernetesConfig(
                enabled = false,
                namespace = null,
                secretName = "zula-jwt-keys",
                autoPull = true,
                autoPush = false,
            ),
        ),
        store = object : JwtKeysStore {
            override fun pull(target: JwtKeysTarget): JwtKeys? = null

            override fun push(target: JwtKeysTarget, keys: JwtKeys) = Unit
        },
        log = org.slf4j.LoggerFactory.getLogger("test"),
    )

    @Test
    fun `generated keys are valid`() {
        val keys = manager.generate()
        assertTrue(manager.validate(keys).isValid)
    }

    @Test
    fun `mismatched public key is invalid`() {
        val keys = manager.generate()
        val other = manager.generate()
        val mismatched = JwtKeys(keys.privateKeyPem, other.publicKeyPem)
        assertFalse(manager.validate(mismatched).isValid)
    }
}
