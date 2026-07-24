package com.zula.core.security.jwt.keys

import com.zula.core.security.jwt.JwtKeyEntry
import com.zula.core.security.jwt.JwtKeySet
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Secret
import io.kubernetes.client.util.ClientBuilder
import org.slf4j.LoggerFactory

data class JwtKeysTarget(
    val namespace: String,
    val secretName: String,
)

private const val PRIVATE_KEY = "JWT_PRIVATE_KEY_PEM"
private const val PUBLIC_KEY = "JWT_PUBLIC_KEY_PEM"
private const val AUTO_GENERATE_FLAG = "JWT_AUTO_GENERATE_KEY"

private val log = LoggerFactory.getLogger("KubernetesJwtKeys")

fun pullJwtKeySet(
    target: JwtKeysTarget,
    kid: String = "default",
    client: ApiClient = ClientBuilder.standard().build(),
): JwtKeySet? =
    runCatching { pullSecret(target, kid, client) }
        .onFailure { log.warn("Failed to pull JWT keys from Kubernetes: {}", it.message) }
        .getOrNull()

fun pushJwtKeySet(
    target: JwtKeysTarget,
    entry: JwtKeyEntry,
    client: ApiClient = ClientBuilder.standard().build(),
) {
    require(entry.canSign()) { "Cannot push verify-only JWT keys to Kubernetes" }
    val privatePem = entry.privatePem ?: error("Cannot push verify-only JWT keys to Kubernetes")
    upsertSecret(target, buildSecret(target, privatePem, entry.publicPem), client)
    log.info("Upserted Kubernetes secret {}/{}", target.namespace, target.secretName)
}

private fun pullSecret(target: JwtKeysTarget, kid: String, client: ApiClient): JwtKeySet? {
    val api = CoreV1Api(client)
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
        privateKey != null && publicKey != null -> JwtKeySet.fromKeyPair(privateKey, publicKey, kid = kid)
        privateKey != null -> JwtKeySet.fromPrivatePem(privateKey, kid = kid)
        publicKey != null -> JwtKeySet.fromSinglePem(publicPem = publicKey, kid = kid)
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

private fun upsertSecret(target: JwtKeysTarget, secret: V1Secret, client: ApiClient) {
    val api = CoreV1Api(client)
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

private fun buildSecret(target: JwtKeysTarget, privatePem: String, publicPem: String): V1Secret =
    V1Secret()
        .metadata(
            V1ObjectMeta()
                .name(target.secretName)
                .namespace(target.namespace),
        )
        .type("Opaque")
        .stringData(
            mapOf(
                PRIVATE_KEY to privatePem,
                PUBLIC_KEY to publicPem,
                AUTO_GENERATE_FLAG to "false",
            ),
        )

private fun V1Secret.readEntry(key: String): String? {
    stringData?.get(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    return data?.get(key)?.takeIf { it.isNotEmpty() }?.let { bytes ->
        String(bytes, Charsets.UTF_8).trim()
    }
}
