package com.zula.core.storage

import java.io.InputStream
import java.net.URI
import org.koin.core.module.Module
import org.koin.dsl.module
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest

data class StoredObject(
    val contentType: String?,
    val contentLength: Long?,
    val body: InputStream,
)

interface ObjectStorage {
    val config: ObjectStorageConfig

    fun putObject(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        body: InputStream,
    )

    fun getObject(objectKey: String): StoredObject?

    fun deleteObject(objectKey: String)
}

class NoopObjectStorage(
    override val config: ObjectStorageConfig = ObjectStorageConfig(enabled = false),
) : ObjectStorage {
    override fun putObject(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        body: InputStream,
    ): Unit = error("Object storage is disabled")

    override fun getObject(objectKey: String): StoredObject? = null

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

    override fun putObject(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        body: InputStream,
    ) {
        client.putObject(
            PutObjectRequest.builder()
                .bucket(config.bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build(),
            RequestBody.fromInputStream(body, contentLength),
        )
    }

    override fun getObject(objectKey: String): StoredObject? {
        return try {
            val stream = client.getObject(
                GetObjectRequest.builder()
                    .bucket(config.bucket)
                    .key(objectKey)
                    .build(),
            )
            StoredObject(
                contentType = stream.response().contentType(),
                contentLength = stream.response().contentLength(),
                body = stream,
            )
        } catch (_: NoSuchKeyException) {
            null
        }
    }

    override fun deleteObject(objectKey: String) {
        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(config.bucket)
                .key(objectKey)
                .build(),
        )
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
