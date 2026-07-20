package com.zula.core.security

import com.zula.lib.id.Ids
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtIssuerTest {
    @Test
    fun `jwtIssuer uses configured APP_URL only`() {
        assertEquals("https://api.zula.app", jwtIssuer("https://api.zula.app/"))
        assertEquals("http://localhost:8000", jwtIssuer("http://localhost:8000"))
        assertNull(jwtIssuer(null))
        assertNull(jwtIssuer(""))
        assertNull(jwtIssuer("   "))
    }

    @Test
    fun `session validator rejects when callback returns false`() {
        val validator = JwtSessionValidator { _, _ -> false }
        assertEquals(false, validator.isValid(Ids.next(), Ids.next()))
    }

    @Test
    fun `session validator accepts matching callback`() {
        val sid = Ids.next()
        val uid = Ids.next()
        val validator = JwtSessionValidator { s, u -> s == sid && u == uid }
        assertEquals(true, validator.isValid(sid, uid))
        assertEquals(false, validator.isValid(sid, Ids.next()))
    }
}
