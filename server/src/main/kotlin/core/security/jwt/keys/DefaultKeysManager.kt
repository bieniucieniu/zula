package com.zula.core.security.jwt.keys

import com.zula.core.security.JwtConfig
import com.zula.core.security.jwt.JwtKeySet
import com.zula.core.security.jwt.JwtKeys
import com.zula.core.security.jwt.decodePrivateKey
import com.zula.core.security.jwt.decodePublicKey
import com.zula.lib.utils.attempt
import org.slf4j.Logger

class DefaultKeysManager(
    private val config: JwtConfig,
    private val store: JwtKeysStore,
    private val log: Logger,
) : KeysManager {
    override fun generate(): JwtKeys = JwtKeys.generateRsa2048()

    override fun validate(keys: JwtKeys): KeysValidation = validateKeySet(JwtKeySet.fromSingleKey(keys))

    fun validateKeySet(keySet: JwtKeySet): KeysValidation {
        val reasons = mutableListOf<String>()

        keySet.keys.forEach { entry ->
            runCatching { entry.publicPem.decodePublicKey() }
                .onFailure { reasons += "kid=${entry.kid}: invalid public PEM: ${it.message}" }

            if (entry.privatePem != null) {
                try {
                    entry.privatePem.decodePrivateKey()
                } catch (it: Throwable) {
                    reasons += "kid=${entry.kid}: invalid private PEM: ${it.message}"
                }
                try {
                    JwtKeys.fromKeyPair(entry.privatePem, entry.publicPem)
                } catch (it: Throwable) {
                    reasons += "kid=${entry.kid}: key pair mismatch"
                }
            }

            val keySize = attempt {
                entry.publicPem.decodePublicKey().modulus.bitLength()
            }
            if (keySize != null && keySize < MIN_RSA_BITS) {
                reasons += "kid=${entry.kid}: RSA key size must be at least $MIN_RSA_BITS bits"
            }
        }

        if (config.signingEnabled) {
            val active = keySet.getActiveSigningKey()
            if (active == null) {
                reasons += "No active signing key"
            } else if (active.privatePem.isNullOrBlank()) {
                reasons += "Active key kid=${active.kid} missing private PEM but JWT_SIGNING_ENABLED=true"
            }
        }

        return if (reasons.isEmpty()) KeysValidation.Valid else KeysValidation.Invalid(reasons)
    }

    override fun pullFromKubernetes(): JwtKeys? {
        if (!config.kubernetes.enabled) return null
        return store.pull(config.kubernetes.toTarget())
    }

    override fun pushToKubernetes(keys: JwtKeys) {
        validate(keys).ensureValid()
        require(keys.canSign()) { "Cannot push verify-only JWT keys to Kubernetes" }
        if (!config.kubernetes.enabled) {
            error("Kubernetes JWT store is disabled (security.jwt.kubernetes.enabled=false)")
        }
        store.push(config.kubernetes.toTarget(), keys)
    }

    override fun resolveKeySet(): JwtKeySet {
        keySetFromConfig()?.let { keySet ->
            validateKeySet(keySet).ensureValid()
            log.info("Using JWT key set from application configuration ({} keys)", keySet.keys.size)
            return keySet
        }

        pullFromKubernetes()?.let { pulled ->
            val keySet = JwtKeySet.fromSingleKey(pulled, kid = config.defaultKeyId)
            validateKeySet(keySet).ensureValid()
            log.info(
                "Loaded JWT keys from Kubernetes secret {}/{}",
                config.kubernetes.resolvedNamespace(),
                config.kubernetes.secretName,
            )
            return keySet
        }

        if (config.autoGenerateKey) {
            val generated = generate()
            validate(generated).ensureValid()
            val keySet = JwtKeySet.fromSingleKey(generated, kid = config.defaultKeyId)
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
            return keySet
        }

        error(
            "JWT keys required: configure JWT_KEYS, JWT_PRIVATE_KEY_PEM / JWT_PUBLIC_KEY_PEM, " +
                    "enable Kubernetes autoPull, or enable JWT_AUTO_GENERATE_KEY",
        )
    }

    override fun resolve(): JwtKeys =
        resolveKeySet().toLegacyJwtKeys() ?: error("JWT keys are not configured")

    private fun keySetFromConfig(): JwtKeySet? {
        config.keysJson?.takeIf { it.isNotBlank() }?.let { return JwtKeySet.fromJson(it) }

        keysFromLegacyPem()?.let { keys ->
            return JwtKeySet.fromSingleKey(keys, kid = config.defaultKeyId)
        }

        return null
    }

    private fun keysFromLegacyPem(): JwtKeys? {
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
