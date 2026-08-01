package com.zula.core.storage

import java.net.URI
import java.time.Duration
import java.time.Instant
import org.koin.core.module.Module
import org.koin.dsl.module
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

data class PresignedPut(
    val uploadUrl: String,
    val objectKey: String,
    val headers: Map<String, String>,
    val expiresAt: Instant,
)

interface ObjectStorage {
    val config: ObjectStorageConfig

    fun presignPut(
        objectKey: String,
        contentType: String,
        contentLength: Long?,
    ): PresignedPut

    fun publicObjectUrl(objectKey: String): String

    fun deleteObject(objectKey: String)
}

class NoopObjectStorage(
    override val config: ObjectStorageConfig = ObjectStorageConfig(enabled = false),
) : ObjectStorage {
    override fun presignPut(
        objectKey: String,
        contentType: String,
        contentLength: Long?,
    ): PresignedPut = error("Object storage is disabled")

    override fun publicObjectUrl(objectKey: String): String = objectKey

    override fun deleteObject(objectKey: String) = Unit
}

class S3ObjectStorage(
    override val config: ObjectStorageConfig,
) : ObjectStorage {
    private val credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(config.accessKey, config.secretKey),
    )
    private val endpoint = URI.create(config.endpoint)
    private val s3Config = S3Configuration.builder()
        .pathStyleAccessEnabled(true)
        .build()

    private val client: S3Client = S3Client.builder()
        .endpointOverride(endpoint)
        .credentialsProvider(credentials)
        .region(Region.of(config.region))
        .serviceConfiguration(s3Config)
        .build()

    private val presigner: S3Presigner = S3Presigner.builder()
        .endpointOverride(endpoint)
        .credentialsProvider(credentials)
        .region(Region.of(config.region))
        .serviceConfiguration(s3Config)
        .build()

    override fun presignPut(
        objectKey: String,
        contentType: String,
        contentLength: Long?,
    ): PresignedPut {
        val put = PutObjectRequest.builder()
            .bucket(config.bucket)
            .key(objectKey)
            .contentType(contentType)
            .apply {
                if (contentLength != null) contentLength(contentLength)
            }
            .build()
        val ttl = Duration.ofSeconds(config.putTtlSeconds)
        val request = PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(put)
            .build()
        val signed = presigner.presignPutObject(request)
        val uploadUrl = rewriteHost(signed.url().toString())
        val headers = buildMap {
            put("Content-Type", contentType)
            if (contentLength != null) put("Content-Length", contentLength.toString())
        }
        return PresignedPut(
            uploadUrl = uploadUrl,
            objectKey = objectKey,
            headers = headers,
            expiresAt = Instant.now().plus(ttl),
        )
    }

    override fun publicObjectUrl(objectKey: String): String {
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey
        }
        return "${config.publicUrl}/${config.bucket}/$objectKey"
    }

    override fun deleteObject(objectKey: String) {
        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(config.bucket)
                .key(objectKey)
                .build(),
        )
    }

    /** Presigner may emit endpoint host; rewrite to public URL host for browsers. */
    private fun rewriteHost(url: String): String {
        if (config.publicUrl == config.endpoint) return url
        return url.replace(config.endpoint, config.publicUrl)
    }
}

fun objectStorageModule(builder: ObjectStorageConfigBuilder.() -> Unit): Module =
    objectStorageModule(ObjectStorageConfigBuilder().apply(builder).build())

fun objectStorageModule(config: ObjectStorageConfig): Module = module {
    single { config }
    single<ObjectStorage> {
        if (config.enabled && config.accessKey.isNotBlank() && config.secretKey.isNotBlank()) {
            S3ObjectStorage(config)
        } else {
            NoopObjectStorage(config)
        }
    }
}
