package com.zula.core.security.jwt

import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class JwtKeyEntryJson(
    val kid: String,
    val publicPem: String,
    val privatePem: String? = null,
    val active: Boolean = false,
)

data class JwtKeyEntry(
    val kid: String,
    val publicPem: String,
    val privatePem: String?,
    val active: Boolean,
) {
    fun canSign(): Boolean = !privatePem.isNullOrBlank()

    fun verificationAlgorithm(): Algorithm =
        Algorithm.RSA256(publicPem.decodePublicKey(), null)

    fun signingAlgorithm(): Algorithm {
        val privateKey = privatePem?.decodePrivateKey()
            ?: error("JWT private key required for kid=$kid")
        return Algorithm.RSA256(publicPem.decodePublicKey(), privateKey)
    }
}

data class JwtKeySet(
    val keys: List<JwtKeyEntry>,
) {
    init {
        require(keys.isNotEmpty()) { "JWT key set must not be empty" }
        require(keys.map { it.kid }.distinct().size == keys.size) { "Duplicate kid in JWT key set" }
        val activeCount = keys.count { it.active }
        require(activeCount == 1) { "JWT key set must have exactly one active key (found $activeCount)" }
    }

    fun getActiveSigningKey(): JwtKeyEntry? = keys.singleOrNull { it.active }

    fun publicKeys(): List<JwtKeyEntry> = keys

    fun verifyKey(kid: String): JwtKeyEntry? = keys.find { it.kid == kid }

    companion object {
        fun fromJson(jsonString: String, json: Json): JwtKeySet {
            val entries: List<JwtKeyEntryJson> = json.decodeFromString(jsonString)
            return fromEntries(entries)
        }

        fun fromEntries(entries: List<JwtKeyEntryJson>): JwtKeySet =
            JwtKeySet(entries.map { it.toEntry() })

        fun fromSinglePem(
            publicPem: String,
            privatePem: String? = null,
            kid: String = "default",
            active: Boolean = true,
        ): JwtKeySet =
            JwtKeySet(
                listOf(
                    JwtKeyEntry(
                        kid = kid,
                        publicPem = publicPem.trim(),
                        privatePem = privatePem?.trim(),
                        active = active,
                    ),
                ),
            )

        fun fromPrivatePem(privateKeyPem: String, kid: String = "default"): JwtKeySet =
            fromSinglePem(
                publicPem = publicPemFromPrivatePem(privateKeyPem),
                privatePem = privateKeyPem.trim(),
                kid = kid,
            )

        fun fromKeyPair(privateKeyPem: String, publicKeyPem: String, kid: String = "default"): JwtKeySet {
            assertKeyPairMatches(privateKeyPem, publicKeyPem)
            return fromSinglePem(
                publicPem = publicKeyPem.trim(),
                privatePem = privateKeyPem.trim(),
                kid = kid,
            )
        }

        fun generateRsa2048(kid: String = "default"): JwtKeySet {
            val (privatePem, publicPem) = generateRsa2048Pems()
            return fromSinglePem(publicPem = publicPem, privatePem = privatePem, kid = kid)
        }
    }
}

private fun JwtKeyEntryJson.toEntry() = JwtKeyEntry(
    kid = kid,
    publicPem = publicPem,
    privatePem = privatePem,
    active = active,
)
