package com.zula.core.security

import com.auth0.jwt.JWT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwtSessionServiceTest {
  @Test
  fun `issues RS256 JWT that verifies with public key`() {
    val config =
      JwtConfig(
        issuer = "https://example.test",
        audience = "zula-api",
        tokenLifetimeSeconds = 900,
        keyId = "test-key",
      )
    val keyMaterial = JwtKeyMaterial.load(config.keyId)
    val service = JwtSessionService(config, keyMaterial)

    val issued = service.issueToken(userId = "user-123")
    val verified = service.verifier().verify(issued.accessToken)

    assertEquals("user-123", verified.subject)
    assertEquals("https://example.test", verified.issuer)
    assertTrue(verified.audience.contains("zula-api"))
    assertEquals("RS256", verified.algorithm)
    assertEquals("test-key", verified.keyId)
    assertNotNull(verified.id)
  }

  @Test
  fun `JWKS exposes RSA public key`() {
    val keyMaterial = JwtKeyMaterial.load("jwks-test")
    val jwks = keyMaterial.toJwksResponse()

    assertEquals(1, jwks.keys.size)
  }
}
