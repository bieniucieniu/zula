package com.zula.core.database

import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class OptimisticIdTest {
    @Test
    fun `detects primary key unique violations`() {
        val error = SQLException(
            """ERROR: duplicate key value violates unique constraint "users_pkey"""",
            "23505",
        )
        assertTrue(isPrimaryKeyConflict(error))
    }

    @Test
    fun `ignores non-primary unique violations`() {
        val error = SQLException(
            """ERROR: duplicate key value violates unique constraint "users_username_key"""",
            "23505",
        )
        assertFalse(isPrimaryKeyConflict(error))
    }

    @Test
    fun `uses preferred id when insert succeeds`() {
        val preferred = Uuid.parse("019535d9-3df7-79fb-b466-fa907fa17f9e")
        val result = insertWithOptimisticId(
            preferredId = preferred,
            insertWithId = { id ->
                assertEquals(preferred, id)
                id
            },
            insertAuto = { error("should not mint") },
        )
        assertEquals(preferred, result)
    }

    @Test
    fun `retries without id on primary key conflict`() {
        val preferred = Uuid.parse("019535d9-3df7-79fb-b466-fa907fa17f9e")
        val minted = Uuid.parse("019535d9-3df7-79fb-b466-fa907fa17f9f")
        var attempts = 0

        val result = insertWithOptimisticId(
            preferredId = preferred,
            insertWithId = {
                attempts++
                throw SQLException(
                    """ERROR: duplicate key value violates unique constraint "users_pkey"""",
                    "23505",
                )
            },
            insertAuto = {
                attempts++
                minted
            },
        )

        assertEquals(2, attempts)
        assertEquals(minted, result)
    }

    @Test
    fun `rethrows non-primary-key errors`() {
        val preferred = Uuid.parse("019535d9-3df7-79fb-b466-fa907fa17f9e")
        assertFailsWith<SQLException> {
            insertWithOptimisticId(
                preferredId = preferred,
                insertWithId = {
                    throw SQLException(
                        """ERROR: duplicate key value violates unique constraint "users_username_key"""",
                        "23505",
                    )
                },
                insertAuto = { error("should not mint") },
            )
        }
    }
}
