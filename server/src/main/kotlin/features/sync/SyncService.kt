package com.zula.features.sync

import com.zula.core.http.HttpException
import com.zula.core.http.ProblemDetails
import com.zula.core.http.problemDetails
import com.zula.core.http.toProblemDetails
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
 *
 * Per-op [HttpException] (4xx) become [SyncOpResult] with embedded [ProblemDetails]
 * so the batch HTTP call still returns 200. Unexpected errors are not caught —
 * StatusPages handles them.
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
                "users" -> reject(op, HttpStatusCode.BadRequest, "users table is not writable via sync")
                else -> reject(op, HttpStatusCode.BadRequest, "unsupported table: ${op.table}")
            }
        } catch (e: HttpException) {
            // Batch semantics only: map client errors to per-op Problem Details.
            when (e) {
                is HttpException.BadRequest,
                is HttpException.Unauthorized,
                is HttpException.Forbidden,
                is HttpException.NotFound,
                is HttpException.Conflict,
                -> fail(op, e.toProblemDetails(instance = op.problemInstance()))
            }
        }
    }

    private fun applyUserProfile(actorId: Uuid, op: SyncOp): SyncOpResult {
        val profileId = Ids.parseOrNull(op.id)
            ?: return reject(op, HttpStatusCode.BadRequest, "invalid profile id")
        val write = op.opData.toProfileWrite()
        when (op.op) {
            SyncOpType.PUT -> userProfiles.putMyProfile(actorId, profileId, write)
            SyncOpType.PATCH -> userProfiles.patchMyProfile(actorId, profileId, write)
            SyncOpType.DELETE -> return reject(
                op,
                HttpStatusCode.BadRequest,
                "profile delete via sync is not allowed",
            )
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

    private fun reject(op: SyncOp, status: HttpStatusCode, detail: String) = fail(
        op,
        problemDetails(
            status = status,
            detail = detail,
            instance = op.problemInstance(),
        ),
    )

    private fun fail(op: SyncOp, problem: ProblemDetails) = SyncOpResult(
        clientId = op.clientId,
        table = op.table,
        id = op.id,
        op = op.op,
        ok = false,
        problem = problem,
    )
}

private fun SyncOp.problemInstance(): String {
    val opRef = clientId?.let { "op/$it" } ?: "op"
    return "/api/sync/batch#$opRef:$table/$id"
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
