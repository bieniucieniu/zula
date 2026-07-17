package com.zula.lib.id

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdsTest {
    @Test
    fun `generateV7 ids are unique and ordered`() {
        val a = Ids.next()
        val b = Ids.next()
        val c = Ids.next()
        assertTrue(a < b)
        assertTrue(b < c)
        assertEquals(3, setOf(a, b, c).size)
    }

    @Test
    fun `createdAtMillis round-trips within a few ms`() {
        val before = System.currentTimeMillis()
        val id = Ids.next()
        val after = System.currentTimeMillis()
        val decoded = Ids.createdAtMillis(id)
        assertTrue(decoded in (before - 2)..(after + 2) || abs(decoded - before) <= 5)
        assertEquals(decoded, Ids.createdAtInstant(id).toEpochMilli())
    }

    @Test
    fun `version nibble is 7`() {
        val id = Ids.next()
        assertEquals(7, Ids.version(id))
    }

    @Test
    fun `parse round-trip`() {
        val id = Ids.next()
        assertEquals(id, Ids.parse(id.toString()))
        assertEquals(id, Ids.parseOrNull(id.toHexDashString()))
    }
}
