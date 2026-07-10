package com.zula

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {
  @Test
  fun `auth providers endpoint`() = testApplication {
    configure()
    assertEquals(HttpStatusCode.OK, client.get("/v1/auth/providers").status)
  }

  @Test
  fun `auth jwks and jwt protected route`() = testApplication {
    configure()

    val jwksResponse = client.get("/v1/auth/jwks")
    assertEquals(HttpStatusCode.OK, jwksResponse.status)
    assertTrue(jwksResponse.bodyAsText().contains("\"kty\":\"RSA\""))

    val authenticateResponse =
      client.post("/v1/auth/authenticate") {
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody("""{"provider":"google","idToken":"test-token"}""")
      }
    assertEquals(HttpStatusCode.OK, authenticateResponse.status)
    val authenticateBody = authenticateResponse.bodyAsText()
    assertTrue(authenticateBody.contains("accessToken"))

    val token =
      Regex(""""accessToken"\s*:\s*"([^"]+)"""")
        .find(authenticateBody)
        ?.groupValues
        ?.get(1)
        ?: error("accessToken missing from authenticate response")

    val meResponse =
      client.get("/v1/auth/me") {
        header(HttpHeaders.Authorization, "Bearer $token")
      }
    assertEquals(HttpStatusCode.OK, meResponse.status)
    assertTrue(meResponse.bodyAsText().contains("sub"))
  }
}
