package com.zula.core.storage

import com.zula.lib.utils.stringOrNull
import io.ktor.server.config.ApplicationConfig

data class ObjectStorageConfig(
    val enabled: Boolean = true,
    val endpoint: String = "http://127.0.0.1:9000",
    val publicUrl: String = "http://127.0.0.1:9000",
    val bucket: String = "zula",
    val accessKey: String = "zula",
    val secretKey: String = "zulazula",
    val region: String = "us-east-1",
    val putTtlSeconds: Long = 900,
    val maxUploadBytes: Long = 10L * 1024 * 1024,
) {
    companion object {
        fun from(config: ApplicationConfig?): ObjectStorageConfig {
            if (config == null) return ObjectStorageConfig(enabled = false)
            val enabled = config.stringOrNull("enabled")?.toBooleanStrictOrNull() ?: true
            val endpoint = config.stringOrNull("endpoint")?.trim().orEmpty()
            if (!enabled || endpoint.isEmpty()) {
                return ObjectStorageConfig(enabled = false)
            }
            return ObjectStorageConfig(
                enabled = true,
                endpoint = endpoint.trimEnd('/'),
                publicUrl = (config.stringOrNull("publicUrl") ?: endpoint).trim().trimEnd('/'),
                bucket = config.stringOrNull("bucket")?.trim()?.ifEmpty { null } ?: "zula",
                accessKey = config.stringOrNull("accessKey")?.trim().orEmpty(),
                secretKey = config.stringOrNull("secretKey")?.trim().orEmpty(),
                region = config.stringOrNull("region")?.trim()?.ifEmpty { null } ?: "us-east-1",
                putTtlSeconds = config.stringOrNull("putTtlSeconds")?.toLongOrNull() ?: 900,
                maxUploadBytes = config.stringOrNull("maxUploadBytes")?.toLongOrNull()
                    ?: (10L * 1024 * 1024),
            )
        }
    }
}

class ObjectStorageConfigBuilder {
    lateinit var config: ObjectStorageConfig

    fun build(): ObjectStorageConfig = config
}
