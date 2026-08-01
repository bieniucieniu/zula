package com.zula.features.media.domain

import kotlinx.serialization.Serializable

@Serializable
data class RequestUploadRequest(
    val contentType: String,
    val contentLength: Long? = null,
)

@Serializable
data class RequestUploadResponse(
    val objectKey: String,
    val uploadUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val publicUrl: String,
    val expiresAt: String,
)
