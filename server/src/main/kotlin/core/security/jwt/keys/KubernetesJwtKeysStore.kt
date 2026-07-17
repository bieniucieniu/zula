package com.zula.core.security.jwt.keys

import com.zula.core.security.jwt.JwtKeys
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Secret
import io.kubernetes.client.util.ClientBuilder
import org.slf4j.LoggerFactory

class KubernetesJwtKeysStore(
    private val client: ApiClient = ClientBuilder.standard().build(),
) : JwtKeysStore {
    private val log = LoggerFactory.getLogger(KubernetesJwtKeysStore::class.java)
    private val api = CoreV1Api(client)

    override fun pull(target: JwtKeysTarget): JwtKeys? =
        runCatching { pullSecret(target) }
            .onFailure { log.warn("Failed to pull JWT keys from Kubernetes: {}", it.message) }
            .getOrNull()

    override fun push(target: JwtKeysTarget, keys: JwtKeys) {
        require(keys.canSign()) { "Cannot push verify-only JWT keys to Kubernetes" }
        upsertSecret(target, buildSecret(target, keys))
        log.info("Upserted Kubernetes secret {}/{}", target.namespace, target.secretName)
    }

    fun close() {
        runCatching {
            client.httpClient.dispatcher.executorService.shutdown()
            client.httpClient.connectionPool.evictAll()
        }
    }

    private fun pullSecret(target: JwtKeysTarget): JwtKeys? {
        val secret = try {
            api.readNamespacedSecret(target.secretName, target.namespace).execute()
        } catch (notFound: ApiException) {
            if (notFound.code == 404) {
                log.info("Kubernetes secret {}/{} not found", target.namespace, target.secretName)
                return null
            }
            throw notFound
        }

        val privateKey = secret.readEntry(PRIVATE_KEY)
        val publicKey = secret.readEntry(PUBLIC_KEY)

        return when {
            privateKey != null && publicKey != null -> JwtKeys.fromKeyPair(privateKey, publicKey)
            privateKey != null -> JwtKeys.fromPrivateKeyPem(privateKey)
            publicKey != null -> JwtKeys.verifyOnly(publicKey)
            else -> {
                log.warn(
                    "Kubernetes secret {}/{} has no {} or {} entries",
                    target.namespace,
                    target.secretName,
                    PRIVATE_KEY,
                    PUBLIC_KEY,
                )
                null
            }
        }
    }

    private fun upsertSecret(target: JwtKeysTarget, secret: V1Secret) {
        try {
            api.replaceNamespacedSecret(target.secretName, target.namespace, secret).execute()
        } catch (notFound: ApiException) {
            if (notFound.code == 404) {
                api.createNamespacedSecret(target.namespace, secret).execute()
            } else {
                throw notFound
            }
        }
    }

    private fun buildSecret(target: JwtKeysTarget, keys: JwtKeys): V1Secret =
        V1Secret()
            .metadata(
                V1ObjectMeta()
                    .name(target.secretName)
                    .namespace(target.namespace),
            )
            .type("Opaque")
            .stringData(
                mapOf(
                    PRIVATE_KEY to keys.privateKeyPem!!,
                    PUBLIC_KEY to keys.publicKeyPem,
                    AUTO_GENERATE_FLAG to "false",
                ),
            )

    private fun V1Secret.readEntry(key: String): String? {
        stringData?.get(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return data?.get(key)?.takeIf { it.isNotEmpty() }?.let { bytes ->
            String(bytes, Charsets.UTF_8).trim()
        }
    }

    private companion object {
        const val PRIVATE_KEY = "JWT_PRIVATE_KEY_PEM"
        const val PUBLIC_KEY = "JWT_PUBLIC_KEY_PEM"
        const val AUTO_GENERATE_FLAG = "JWT_AUTO_GENERATE_KEY"
    }
}
