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
                get("/fields") {
                    badRequest(
                        "Validation failed",
                        errors = mapOf(
                            "displayName" to listOf(
                                ProblemErrorCode.REQUIRED,
                                ProblemErrorCode.TOO_SHORT,
                            ),
                            "bio" to listOf(ProblemErrorCode.TOO_LONG),
                        ),
                    )
                }
                get("/boom") { error("secret") }
            }
        }

        val bad = client.get("/bad")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertTrue(bad.bodyAsText().contains("nope"))

        val denied = client.get("/denied")
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertTrue(denied.bodyAsText().contains("no access"))

        val fields = client.get("/fields")
        assertEquals(HttpStatusCode.BadRequest, fields.status)
        val body = fields.bodyAsText()
        assertTrue(body.contains("\"displayName\""))
        assertTrue(body.contains("required"))
        assertTrue(body.contains("too_short"))
        assertTrue(body.contains("\"bio\""))
        assertTrue(body.contains("too_long"))

        val boom = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, boom.status)
        assertTrue(boom.bodyAsText().contains("Internal Server Error"))
        assertTrue(!boom.bodyAsText().contains("secret"))
    }

    @Test
    fun `problem details serializes field errors map`() {
        val problem = problemDetails(
            status = HttpStatusCode.BadRequest,
            detail = "Validation failed",
            instance = "/api/users",
            errors = mapOf("email" to listOf(ProblemErrorCode.INVALID)),
        )
        assertEquals(mapOf("email" to listOf(ProblemErrorCode.INVALID)), problem.errors)
        assertEquals("/api/users", problem.instance)
    }

    @Test
    fun `ProblemErrorCode wire values are snake_case`() {
        val encoded = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ProblemErrorCode.serializer()),
            listOf(
                ProblemErrorCode.REQUIRED,
                ProblemErrorCode.TOO_SHORT,
                ProblemErrorCode.NOT_FOUND,
            ),
        )
        assertEquals("""["required","too_short","not_found"]""", encoded)
    }
}
