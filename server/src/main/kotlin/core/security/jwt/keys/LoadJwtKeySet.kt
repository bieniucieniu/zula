package com.zula.core.security.jwt.keys

import com.zula.core.security.JwtConfig
import com.zula.core.security.JwtKubernetesConfig
import com.zula.core.security.jwt.JwtKeySet
import com.zula.core.security.jwt.assertKeyPairMatches
import com.zula.core.security.jwt.decodePrivateKey
import com.zula.core.security.jwt.decodePublicKey
import com.zula.lib.utils.attempt
import kotlinx.serialization.json.Json
import org.slf4j.Logger

private const val MIN_RSA_BITS = 2048

sealed interface KeysValidation {
    data object Valid : KeysValidation

    data class Invalid(
        val reasons: List<String>,
    ) : KeysValidation {
        constructor(reason: String) : this(listOf(reason))
    }

    val isValid: Boolean
        get() = this is Valid
}

fun KeysValidation.ensureValid() {
    if (this is KeysValidation.Invalid) {
        error("Invalid JWT keys: ${reasons.joinToString("; ")}")
    }
}

fun validateJwtKeySet(keySet: JwtKeySet, signingEnabled: Boolean): KeysValidation {
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
                assertKeyPairMatches(entry.privatePem, entry.publicPem)
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

    if (signingEnabled) {
        val active = keySet.getActiveSigningKey()
        if (active == null) {
            reasons += "No active signing key"
        } else if (active.privatePem.isNullOrBlank()) {
            reasons += "Active key kid=${active.kid} missing private PEM but JWT_SIGNING_ENABLED=true"
        }
    }

    return if (reasons.isEmpty()) KeysValidation.Valid else KeysValidation.Invalid(reasons)
}

/**
 * Load order: JWT_KEYS / PEM env → k8s pull (if enabled && autoPull) → auto-generate (if allowed).
 */
fun loadJwtKeySet(config: JwtConfig, json: Json, log: Logger): JwtKeySet {
    keySetFromConfig(config, json)?.let { keySet ->
        validateJwtKeySet(keySet, config.signingEnabled).ensureValid()
        log.info("Using JWT key set from application configuration ({} keys)", keySet.keys.size)
        return keySet
    }

    if (config.kubernetes.enabled && config.kubernetes.autoPull) {
        pullJwtKeySet(config.kubernetes.toTarget(), config.defaultKeyId)?.let { keySet ->
            validateJwtKeySet(keySet, config.signingEnabled).ensureValid()
            log.info(
                "Loaded JWT keys from Kubernetes secret {}/{}",
                config.kubernetes.resolvedNamespace(),
                config.kubernetes.secretName,
            )
            return keySet
        }
    }

    if (config.autoGenerateKey) {
        val keySet = JwtKeySet.generateRsa2048(kid = config.defaultKeyId)
        validateJwtKeySet(keySet, config.signingEnabled).ensureValid()
        if (config.kubernetes.enabled && config.kubernetes.autoPush) {
            val active = keySet.getActiveSigningKey()
                ?: error("Generated key set has no active signing key")
            pushJwtKeySet(config.kubernetes.toTarget(), active)
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

private fun keySetFromConfig(config: JwtConfig, json: Json): JwtKeySet? {
    config.keysJson?.takeIf { it.isNotBlank() }?.let { return JwtKeySet.fromJson(it, json) }

    val privateKey = config.privateKeyPem?.takeIf { it.isNotBlank() }
    val publicKey = config.publicKeyPem?.takeIf { it.isNotBlank() }
    return when {
        privateKey != null && publicKey != null ->
            JwtKeySet.fromKeyPair(privateKey, publicKey, kid = config.defaultKeyId)
        privateKey != null ->
            JwtKeySet.fromPrivatePem(privateKey, kid = config.defaultKeyId)
        publicKey != null ->
            JwtKeySet.fromSinglePem(publicPem = publicKey, kid = config.defaultKeyId)
        else -> null
    }
}

internal fun JwtKubernetesConfig.toTarget(): JwtKeysTarget =
    JwtKeysTarget(
        namespace = resolvedNamespace(),
        secretName = secretName,
    )

internal fun JwtKubernetesConfig.resolvedNamespace(): String =
    namespace?.takeIf { it.isNotBlank() } ?: PodNamespace.current()
