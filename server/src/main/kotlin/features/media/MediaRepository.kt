package com.zula.features.media

import com.zula.Database
import com.zula.Media_objects
import kotlin.uuid.Uuid

class MediaRepository(
    private val database: Database,
) {
    private val queries inline get() = database.mediaQueries

    fun insertPending(
        objectKey: String,
        ownerUserId: Uuid,
        contentType: String?,
        byteSize: Long?,
        createdAt: Long,
    ) {
        queries.insertPendingMediaObject(objectKey, ownerUserId, contentType, byteSize, createdAt)
    }

    fun get(objectKey: String): Media_objects? =
        queries.getMediaObject(objectKey).executeAsOneOrNull()

    fun commitPending(objectKey: String, ownerUserId: Uuid, committedAt: Long): Media_objects? =
        queries.commitMediaObject(committedAt, objectKey, ownerUserId).executeAsOneOrNull()

    fun incrementActive(objectKey: String, ownerUserId: Uuid): Media_objects? =
        queries.incrementActiveMediaRef(objectKey, ownerUserId).executeAsOneOrNull()

    fun release(objectKey: String, deleteAfter: Long): Media_objects? =
        queries.releaseMediaObject(deleteAfter, objectKey).executeAsOneOrNull()
}
