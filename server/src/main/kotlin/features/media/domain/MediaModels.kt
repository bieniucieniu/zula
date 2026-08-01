package com.zula.features.media.domain

import kotlinx.serialization.Serializable

@Serializable
data class UploadMediaResponse(
    val objectKey: String,
    val publicUrl: String,
)
