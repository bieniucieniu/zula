package com.zula.features.media

import com.zula.core.http.badRequest
import com.zula.core.http.forbidden
import com.zula.core.http.notFound
import com.zula.core.storage.ObjectStorage
import com.zula.core.storage.StoredObject
import com.zula.features.media.domain.UploadMediaResponse
import com.zula.lib.id.Ids
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.uuid.Uuid

class MediaService(
    private val repository: MediaRepository,
    private val storage: ObjectStorage,
) {
    private val allowedContentTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
    )

    fun upload(
        ownerUserId: Uuid,
        contentTypeRaw: String,
        bytes: ByteArray,
    ): UploadMediaResponse {
        if (!storage.config.enabled) {
            badRequest("Object storage is disabled")
        }
        val contentType = contentTypeRaw.trim().lowercase().substringBefore(';').trim()
        if (contentType !in allowedContentTypes) {
            badRequest("contentType must be one of ${allowedContentTypes.joinToString()}")
        }
        if (bytes.isEmpty()) badRequest("body must not be empty")
        if (bytes.size.toLong() > storage.config.maxUploadBytes) {
            badRequest("body must be at most ${storage.config.maxUploadBytes} bytes")
        }
        val objectKey = "uploads/$ownerUserId/${Ids.next()}"
        val now = Instant.now().epochSecond
        repository.insertPending(
            objectKey = objectKey,
            ownerUserId = ownerUserId,
            contentType = contentType,
            byteSize = bytes.size.toLong(),
            createdAt = now,
        )
        storage.putObject(
            objectKey = objectKey,
            contentType = contentType,
            contentLength = bytes.size.toLong(),
            body = ByteArrayInputStream(bytes),
        )
        return UploadMediaResponse(
            objectKey = objectKey,
            publicUrl = publicUrl(objectKey)!!,
        )
    }

    fun openObject(objectKey: String): Pair<String?, StoredObject> {
        val key = normalizeKey(objectKey)
        val row = repository.get(key)
        if (row == null || row.status == "deleted") {
            notFound("Media object not found")
        }
        val stored = storage.getObject(key) ?: notFound("Media object not found")
        val contentType = stored.contentType ?: row.content_type
        return contentType to stored
    }

    fun publicUrl(objectKey: String?): String? {
        val key = objectKey?.trim()?.ifEmpty { null } ?: return null
        val encoded = java.net.URLEncoder.encode(key, Charsets.UTF_8).replace("+", "%20")
        return "/api/media/objects?key=$encoded"
    }

    fun requireOwnedUploadKey(ownerUserId: Uuid, objectKey: String): String {
        val key = objectKey.trim()
        validateUploadKey(ownerUserId, key)
        val row = repository.get(key) ?: notFound("Media object not found")
        if (row.owner_user_id != ownerUserId) forbidden("Media object not owned by caller")
        if (row.status == "deleted") badRequest("Media object is deleted")
        return key
    }

    fun commitKeys(ownerUserId: Uuid, keys: List<String>) {
        if (keys.isEmpty()) return
        val now = Instant.now().epochSecond
        keys.forEach { raw ->
            val key = requireOwnedUploadKey(ownerUserId, raw)
            val row = repository.get(key) ?: notFound("Media object not found")
            when (row.status) {
                "pending" -> {
                    repository.commitPending(key, ownerUserId, now)
                        ?: conflictCommit(key)
                }
                "active" -> {
                    repository.incrementActive(key, ownerUserId)
                        ?: conflictCommit(key)
                }
                else -> badRequest("Media object cannot be committed")
            }
        }
    }

    fun releaseKey(objectKey: String?) {
        val key = objectKey?.trim()?.ifEmpty { null } ?: return
        val deleteAfter = Instant.now().epochSecond + DELETE_GRACE_SECONDS
        repository.release(key, deleteAfter)
    }

    /**
     * One GC pass: purge stale pending uploads and deleted objects past grace.
     * @return number of rows removed
     */
    fun gcOnce(batchSize: Long = GC_BATCH_SIZE): Int {
        val now = Instant.now().epochSecond
        var deleted = 0
        val pendingCutoff = now - PENDING_ORPHAN_AGE_SECONDS
        repository.listGcPending(pendingCutoff, batchSize).forEach { row ->
            runCatching { storage.deleteObject(row.object_key) }
            repository.deleteRow(row.object_key)
            deleted++
        }
        repository.listGcDeleted(now, batchSize).forEach { row ->
            runCatching { storage.deleteObject(row.object_key) }
            repository.deleteRow(row.object_key)
            deleted++
        }
        return deleted
    }

    private fun normalizeKey(objectKey: String): String {
        val key = objectKey.trim().trimStart('/')
        if (key.isEmpty() || key.contains("..") || !key.startsWith("uploads/")) {
            badRequest("Invalid object key")
        }
        return key
    }

    private fun validateUploadKey(ownerUserId: Uuid, key: String) {
        val prefix = "uploads/$ownerUserId/"
        if (!key.startsWith(prefix) || key.length <= prefix.length || key.contains("..")) {
            badRequest("objectKey must be under $prefix")
        }
    }

    private fun conflictCommit(key: String): Nothing =
        badRequest("Media object not ready to commit: $key")

    companion object {
        const val DELETE_GRACE_SECONDS = 7L * 24 * 60 * 60
        const val PENDING_ORPHAN_AGE_SECONDS = 24L * 60 * 60
        const val GC_BATCH_SIZE = 100L
    }
}
