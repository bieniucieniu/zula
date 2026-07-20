package com.zula.core.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusPagesErrorHandlerTest {
    @Test
    fun `HttpException subtypes map to problem details`() = testApplication {
        application {
            configureHttp()
            routing {
                get("/bad") { badRequest("nope") }
                get("/denied") { forbidden("no access") }
                get("/boom") { error("secret") }
            }
        }

        val bad = client.get("/bad")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertTrue(bad.bodyAsText().contains("nope"))

        val denied = client.get("/denied")
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertTrue(denied.bodyAsText().contains("no access"))

        val boom = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, boom.status)
        assertTrue(boom.bodyAsText().contains("Internal Server Error"))
        assertTrue(!boom.bodyAsText().contains("secret"))
    }
}
