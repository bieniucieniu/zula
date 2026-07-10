package com.zula.core.security.jwt.keys

import com.zula.core.security.JwtConfig
import com.zula.core.security.jwt.JwtKeys
import com.zula.core.security.jwt.decodePrivateKey
import com.zula.core.security.jwt.decodePublicKey
import org.slf4j.Logger

class DefaultKeysManager(
    private val config: JwtConfig,
    private val store: JwtKeysStore,
    private val log: Logger,
) : KeysManager {
    override fun generate(): JwtKeys = JwtKeys.generateRsa2048()

    override fun validate(keys: JwtKeys): KeysValidation {
        val reasons = mutableListOf<String>()

        runCatching { keys.publicKeyPem.decodePublicKey() }
            .onFailure { reasons += "Public key is not a valid RSA PEM: ${it.message}" }

        if (keys.canSign) {
            runCatching { keys.privateKeyPem!!.decodePrivateKey() }
                .onFailure { reasons += "Private key is not a valid RSA PEM: ${it.message}" }
        }

        if (keys.canSign) {
            runCatching { JwtKeys.fromKeyPair(keys.privateKeyPem!!, keys.publicKeyPem) }
                .onFailure { reasons += it.message ?: "Public and private keys do not match" }
        }

        val keySize = runCatching { keys.publicKeyPem.decodePublicKey().modulus.bitLength() }.getOrNull()
        if (keySize != null && keySize < MIN_RSA_BITS) {
            reasons += "RSA key size must be at least $MIN_RSA_BITS bits (found $keySize)"
        }

        return if (reasons.isEmpty()) KeysValidation.Valid else KeysValidation.Invalid(reasons)
    }

    override fun pullFromKubernetes(): JwtKeys? {
        if (!config.kubernetes.enabled) return null
        return store.pull(config.kubernetes.toTarget())
    }

    override fun pushToKubernetes(keys: JwtKeys) {
        validate(keys).ensureValid()
        require(keys.canSign) { "Cannot push verify-only JWT keys to Kubernetes" }
        if (!config.kubernetes.enabled) {
            error("Kubernetes JWT store is disabled (security.jwt.kubernetes.enabled=false)")
        }
        store.push(config.kubernetes.toTarget(), keys)
    }

    override fun resolve(): JwtKeys {
        keysFromConfig()?.let { configured ->
            validate(configured).ensureValid()
            log.info("Using JWT keys from application configuration")
            return configured
        }

        pullFromKubernetes()?.let { pulled ->
            validate(pulled).ensureValid()
            log.info(
                "Loaded JWT keys from Kubernetes secret {}/{}",
                config.kubernetes.resolvedNamespace(),
                config.kubernetes.secretName,
            )
            return pulled
        }

        if (config.autoGenerateKey) {
            val generated = generate()
            validate(generated).ensureValid()
            if (config.kubernetes.enabled && config.kubernetes.autoPush) {
                pushToKubernetes(generated)
                log.info(
                    "Generated JWT keys and pushed them to Kubernetes secret {}/{}",
                    config.kubernetes.resolvedNamespace(),
                    config.kubernetes.secretName,
                )
            } else {
                log.warn("JWT keys not configured; using ephemeral in-memory RSA key pair")
            }
            return generated
        }

        error(
            "JWT keys required: configure JWT_PRIVATE_KEY_PEM / JWT_PUBLIC_KEY_PEM, " +
                "enable Kubernetes autoPull (security.jwt.kubernetes), or enable JWT_AUTO_GENERATE_KEY",
        )
    }

    private fun keysFromConfig(): JwtKeys? {
        val privateKey = config.privateKeyPem
        val publicKey = config.publicKeyPem
        return when {
            privateKey != null && publicKey != null -> JwtKeys.fromKeyPair(privateKey, publicKey)
            privateKey != null -> JwtKeys.fromPrivateKeyPem(privateKey)
            publicKey != null -> JwtKeys.verifyOnly(publicKey)
            else -> null
        }
    }

    private companion object {
        const val MIN_RSA_BITS = 2048
    }
}

private fun com.zula.core.security.JwtKubernetesConfig.toTarget(): JwtKeysTarget =
    JwtKeysTarget(
        namespace = resolvedNamespace(),
        secretName = secretName,
    )

private fun com.zula.core.security.JwtKubernetesConfig.resolvedNamespace(): String =
    namespace?.takeIf { it.isNotBlank() } ?: PodNamespace.current()
