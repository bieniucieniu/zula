package com.zula.features.sync

import com.zula.core.http.HttpException
import com.zula.features.sync.domain.SyncBatchRequest
import com.zula.features.sync.domain.SyncBatchResponse
import com.zula.features.sync.domain.SyncOp
import com.zula.features.sync.domain.SyncOpResult
import com.zula.features.sync.domain.SyncOpType
import com.zula.features.user.ProfileWrite
import com.zula.features.user.UserProfileWriter
import com.zula.lib.id.Ids
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.Uuid

/**
 * Applies a PowerSync upload batch by dispatching each op to domain services.
 * Never writes raw SQL from [SyncOp.opData].
 */
class SyncService(
    private val userProfiles: UserProfileWriter,
) {
    fun applyBatch(actorId: Uuid, request: SyncBatchRequest): SyncBatchResponse {
        if (request.ops.isEmpty()) {
            return SyncBatchResponse(emptyList())
        }
        val results = request.ops.map { op -> applyOne(actorId, op) }
        return SyncBatchResponse(results)
    }

    private fun applyOne(actorId: Uuid, op: SyncOp): SyncOpResult {
        return try {
            when (op.table) {
                "user_profiles" -> applyUserProfile(actorId, op)
                "users" -> reject(op, "users table is not writable via sync")
                else -> reject(op, "unsupported table: ${op.table}")
            }
        } catch (e: HttpException) {
            if (e.status.value in 500..599) throw e
            reject(op, e.message ?: e.status.description, retryable = false)
        } catch (e: Exception) {
            throw HttpException(
                e.message ?: "sync op failed",
                HttpStatusCode.ServiceUnavailable,
            )
        }
    }

    private fun applyUserProfile(actorId: Uuid, op: SyncOp): SyncOpResult {
        val profileId = Ids.parseOrNull(op.id)
            ?: return reject(op, "invalid profile id")
        val write = op.opData.toProfileWrite()
        when (op.op) {
            SyncOpType.PUT -> userProfiles.putMyProfile(actorId, profileId, write)
            SyncOpType.PATCH -> userProfiles.patchMyProfile(actorId, profileId, write)
            SyncOpType.DELETE -> return reject(op, "profile delete via sync is not allowed")
        }
        return ok(op)
    }

    private fun ok(op: SyncOp) = SyncOpResult(
        clientId = op.clientId,
        table = op.table,
        id = op.id,
        op = op.op,
        ok = true,
    )

    private fun reject(op: SyncOp, error: String, retryable: Boolean = false) = SyncOpResult(
        clientId = op.clientId,
        table = op.table,
        id = op.id,
        op = op.op,
        ok = false,
        error = error,
        retryable = retryable,
    )
}

/** PowerSync column names (snake_case) → domain [ProfileWrite]. */
internal fun JsonObject?.toProfileWrite(): ProfileWrite {
    if (this == null) return ProfileWrite()
    return ProfileWrite(
        displayName = stringOrNull("display_name"),
        avatarUrl = stringOrNull("avatar_url"),
        bio = stringOrNull("bio"),
        timezone = stringOrNull("timezone"),
        preferredLanguage = stringOrNull("preferred_language"),
        locationTag = stringOrNull("location_tag"),
        sellerHeadline = stringOrNull("seller_headline"),
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull
