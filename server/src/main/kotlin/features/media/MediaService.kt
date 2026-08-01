package com.zula.features.media

import com.zula.core.http.badRequest
import com.zula.core.http.forbidden
import com.zula.core.http.notFound
import com.zula.core.storage.ObjectStorage
import com.zula.features.media.domain.RequestUploadRequest
import com.zula.features.media.domain.RequestUploadResponse
import com.zula.lib.id.Ids
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

    fun requestUpload(ownerUserId: Uuid, req: RequestUploadRequest): RequestUploadResponse {
        if (!storage.config.enabled) {
            badRequest("Object storage is disabled")
        }
        val contentType = req.contentType.trim().lowercase()
        if (contentType !in allowedContentTypes) {
            badRequest("contentType must be one of ${allowedContentTypes.joinToString()}")
        }
        val length = req.contentLength
        if (length != null) {
            if (length <= 0) badRequest("contentLength must be positive")
            if (length > storage.config.maxUploadBytes) {
                badRequest("contentLength must be at most ${storage.config.maxUploadBytes} bytes")
            }
        }
        val objectKey = "uploads/$ownerUserId/${Ids.next()}"
        val now = Instant.now().epochSecond
        repository.insertPending(
            objectKey = objectKey,
            ownerUserId = ownerUserId,
            contentType = contentType,
            byteSize = length,
            createdAt = now,
        )
        val put = storage.presignPut(objectKey, contentType, length)
        return RequestUploadResponse(
            objectKey = objectKey,
            uploadUrl = put.uploadUrl,
            headers = put.headers,
            publicUrl = storage.publicObjectUrl(objectKey),
            expiresAt = put.expiresAt.toString(),
        )
    }

    fun publicUrl(objectKey: String?): String? {
        val key = objectKey?.trim()?.ifEmpty { null } ?: return null
        return storage.publicObjectUrl(key)
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
    }
}
