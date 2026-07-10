package com.zula.core.security.jwt.keys

import com.zula.core.security.jwt.JwtKeys
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.slf4j.LoggerFactory
import java.util.Base64

class KubernetesJwtKeysStore(
    private val client: KubernetesClient = KubernetesClientBuilder().build(),
) : JwtKeysStore {
    private val log = LoggerFactory.getLogger(KubernetesJwtKeysStore::class.java)

    override fun pull(target: JwtKeysTarget): JwtKeys? =
        runCatching { pullSecret(target) }
            .onFailure { log.warn("Failed to pull JWT keys from Kubernetes: {}", it.message) }
            .getOrNull()

    override fun push(target: JwtKeysTarget, keys: JwtKeys) {
        require(keys.canSign) { "Cannot push verify-only JWT keys to Kubernetes" }

        val secret = SecretBuilder()
            .withNewMetadata()
            .withName(target.secretName)
            .withNamespace(target.namespace)
            .endMetadata()
            .withType("Opaque")
            .addToStringData(PRIVATE_KEY, keys.privateKeyPem!!)
            .addToStringData(PUBLIC_KEY, keys.publicKeyPem)
            .addToStringData(AUTO_GENERATE_FLAG, "false")
            .build()

        client.secrets().inNamespace(target.namespace).resource(secret).createOrReplace()
        log.info("Upserted Kubernetes secret {}/{}", target.namespace, target.secretName)
    }

    private fun pullSecret(target: JwtKeysTarget): JwtKeys? {
        val secret = client.secrets().inNamespace(target.namespace).withName(target.secretName).get()
            ?: run {
                log.info("Kubernetes secret {}/{} not found", target.namespace, target.secretName)
                return null
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

    fun close() {
        client.close()
    }

    private fun io.fabric8.kubernetes.api.model.Secret.readEntry(key: String): String? {
        stringData?.get(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return data?.get(key)?.takeIf { it.isNotBlank() }?.let { encoded ->
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8).trim()
        }
    }

    private companion object {
        const val PRIVATE_KEY = "JWT_PRIVATE_KEY_PEM"
        const val PUBLIC_KEY = "JWT_PUBLIC_KEY_PEM"
        const val AUTO_GENERATE_FLAG = "JWT_AUTO_GENERATE_KEY"
    }
}
