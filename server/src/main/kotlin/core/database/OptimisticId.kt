package com.zula.core.database

import java.sql.SQLException
import kotlin.uuid.Uuid

internal fun isPrimaryKeyConflict(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is SQLException && current.sqlState == "23505") {
            val message = current.message.orEmpty()
            if (message.contains("_pkey", ignoreCase = true)) {
                return true
            }
        }
        current = current.cause
    }
    return false
}

/**
 * Insert with a client-minted id for optimistic create flows on **non-critical** data only
 * (e.g. ephemeral auth challenges). On primary-key conflict, retry without an id so
 * PostgreSQL applies `uuidv7()`. Do not use for users, profiles, identities, or sessions.
 */
internal inline fun <T> insertWithOptimisticId(
    preferredId: Uuid?,
    insertWithId: (Uuid) -> T,
    insertAuto: () -> T,
): T {
    if (preferredId != null) {
        try {
            return insertWithId(preferredId)
        } catch (error: Exception) {
            if (!isPrimaryKeyConflict(error)) {
                throw error
            }
        }
    }
    return insertAuto()
}
