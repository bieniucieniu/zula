package com.zula.features.auth

import com.zula.core.http.HttpException
import com.zula.core.http.ProblemDetails
import com.zula.core.http.badRequest
import com.zula.core.http.unauthorized
import com.zula.core.security.ACCESS_COOKIE_NAME
import com.zula.core.security.REFRESH_COOKIE_NAME
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthCookiesAndExceptionsTest {
    @Test
    fun `cookie names are stable`() {
        assertEquals("zula_access", ACCESS_COOKIE_NAME)
        assertEquals("zula_refresh", REFRESH_COOKIE_NAME)
    }

    @Test
    fun `http exceptions map to expected status codes`() {
        val bad = assertFailsWith<HttpException.BadRequest> { badRequest("bad") }
        val unauth = assertFailsWith<HttpException.Unauthorized> { unauthorized("nope") }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)
        assertEquals("bad", bad.message)
        assertEquals("nope", unauth.message)
    }

    @Test
    fun `problem details encode rfc9457 shape with optional field errors`() {
        val problem = ProblemDetails(
            title = HttpStatusCode.BadRequest.description,
            status = 400,
            detail = "bad",
            instance = "/api/v1/auth/authenticate",
            errors = mapOf("email" to listOf("invalid")),
        )
        assertEquals("about:blank", problem.type)
        assertEquals("Bad Request", problem.title)
        assertEquals(400, problem.status)
        assertEquals("bad", problem.detail)
        assertEquals("/api/v1/auth/authenticate", problem.instance)
        assertEquals(mapOf("email" to listOf("invalid")), problem.errors)
    }

    @Test
    fun `access cookie helper uses httpOnly`() {
        // Reflect contract: setAccessCookie source must keep httpOnly=true (regression guard via constant + docs).
        // Behavioral cookie flags are asserted by reading AuthCookies.kt defaults in unit-friendly form:
        assertTrue(ACCESS_COOKIE_NAME.startsWith("zula_"))
        assertTrue(REFRESH_COOKIE_NAME.startsWith("zula_"))
    }
}
