package com.zula.core.security.jwt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwkSetProviderTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `jwks includes required kty per RFC 7517 section 4_1`() {
        val keySet = JwtKeySet.generateRsa2048(kid = "test-kid")
        val response = JwkSetProvider(keySet).jwks()
        val encoded = json.encodeToString(JwksResponse.serializer(), response)
        val keys = json.parseToJsonElement(encoded).jsonObject.getValue("keys").jsonArray

        assertEquals(1, keys.size)
        val jwk = keys.first().jsonObject
        assertTrue("kty" in jwk, "RFC 7517 §4.1 requires kty to be present")
        assertEquals("RSA", jwk.getValue("kty").jsonPrimitive.content)
        assertEquals("sig", jwk.getValue("use").jsonPrimitive.content)
        assertEquals("RS256", jwk.getValue("alg").jsonPrimitive.content)
        assertEquals("test-kid", jwk.getValue("kid").jsonPrimitive.content)
        assertTrue(jwk.getValue("n").jsonPrimitive.content.isNotBlank())
        assertTrue(jwk.getValue("e").jsonPrimitive.content.isNotBlank())
    }
}
