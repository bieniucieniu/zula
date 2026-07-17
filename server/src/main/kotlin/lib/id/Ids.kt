package com.zula.lib.id

import app.cash.sqldelight.ColumnAdapter
import java.time.Instant
import java.util.*
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Application IDs are UUIDv7 ([Uuid.generateV7]).
 * Time-ordered; create time is in the high 48 bits (Unix ms).
 */
object Ids {
    fun next(): Uuid = Uuid.generateV7()

    fun parse(value: String): Uuid = Uuid.parse(value)

    fun parseOrNull(value: String): Uuid? = Uuid.parseOrNull(value)

    /** Unix epoch ms encoded in a UUIDv7 (RFC 9562). */
    fun createdAtMillis(id: Uuid): Long {
        val bytes = id.toByteArray()
        var ms = 0L
        for (i in 0..5) {
            ms = (ms shl 8) or (bytes[i].toLong() and 0xFF)
        }
        return ms
    }

    fun createdAtInstant(id: Uuid): Instant = Instant.ofEpochMilli(createdAtMillis(id))

    fun version(uuid: Uuid): Int {
        return uuid.toLongs { mostSignificantBits, _ ->
            ((mostSignificantBits shr 12) and 0xF).toInt()
        }
    }
}

/**
 * JDBC/Postgres still speaks [java.util.UUID]; keep that conversion here only.
 * App code uses [kotlin.uuid.Uuid].
 */
internal object KotlinUuidAdapter : ColumnAdapter<Uuid, UUID> {
    override fun decode(databaseValue: UUID): Uuid = databaseValue.toKotlinUuid()
    override fun encode(value: Uuid): UUID = value.toJavaUuid()
}
