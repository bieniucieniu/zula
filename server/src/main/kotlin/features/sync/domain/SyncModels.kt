package com.zula.features.sync.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** PowerSync CrudEntry-shaped op from the client upload queue. */
@Serializable
data class SyncOp(
    /** Client queue id — echoed in [SyncOpResult] for correlation. */
    val clientId: Long? = null,
    val op: SyncOpType,
    val table: String,
    val id: String,
    val opData: JsonObject? = null,
    val metadata: String? = null,
)

@Serializable
enum class SyncOpType {
    @SerialName("PUT")
    PUT,

    @SerialName("PATCH")
    PATCH,

    @SerialName("DELETE")
    DELETE,
}

@Serializable
data class SyncBatchRequest(
    val ops: List<SyncOp>,
)

@Serializable
data class SyncOpResult(
    val clientId: Long? = null,
    val table: String,
    val id: String,
    val op: SyncOpType,
    val ok: Boolean,
    /** Permanent rejection — client should still complete the batch (do not retry forever). */
    val error: String? = null,
    /** If true, client should throw / retry (5xx-class). Usually whole HTTP fails instead. */
    val retryable: Boolean = false,
)

@Serializable
data class SyncBatchResponse(
    val results: List<SyncOpResult>,
)
