package com.zula.core.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresVersionTest {
    @Test
    fun `accepts postgres 18 and newer`() {
        assertTrue(isPostgresVersionSupported(180_000))
        assertTrue(isPostgresVersionSupported(180_001))
        assertTrue(isPostgresVersionSupported(190_000))
    }

    @Test
    fun `rejects postgres 17 and older`() {
        assertFalse(isPostgresVersionSupported(179_999))
        assertFalse(isPostgresVersionSupported(170_005))
        assertFalse(isPostgresVersionSupported(160_001))
    }

    @Test
    fun `derives major version from server_version_num`() {
        assertEquals(18, postgresMajorVersion(180_000))
        assertEquals(17, postgresMajorVersion(170_005))
    }
}
