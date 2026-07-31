package com.zula.features.auth.provider

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.*
import java.util.concurrent.ConcurrentHashMap


const val KEYS_LOADED_MAX_AGE = 900_000 // 15 min

class OidcIdTokenVerifier(
    val httpClient: HttpClient,
    val jwksUrl: String,
    val issuers: List<String>,
    val audiences: List<String>,
    val json: Json
) {
    private val keyCache = ConcurrentHashMap<String, Algorithm>()
    private var keysLoadedAt = 0L

    suspend fun verify(idToken: String): DecodedJWT {
        refreshKeysIfNeeded()
        val decoded = JWT.decode(idToken)
        val kid = decoded.keyId ?: error("ID token missing kid")
        val algorithm = keyCache[kid] ?: error("Unknown key id: $kid")
        val verifier = JWT.require(algorithm)
            .withIssuer(*issuers.toTypedArray())
            .withAudience(*audiences.toTypedArray())
            .build()
        return verifier.verify(decoded)
    }

    private suspend fun refreshKeysIfNeeded() {
        if (keyCache.isEmpty() || System.currentTimeMillis() - keysLoadedAt > KEYS_LOADED_MAX_AGE) {
            refreshKeys()
        }
    }

    private suspend fun refreshKeys() {
        val remote: String = httpClient.get(jwksUrl).body()
        val jwks: RemoteJwks = json.decodeFromString(remote)
        val next = ConcurrentHashMap<String, Algorithm>()
        jwks.keys.forEach { key ->
            if (key.kty == "RSA") {
                next[key.kid] = Algorithm.RSA256(key.toPublicKey(), null)
            }
        }
        // Replace cache so rotated-away kids are dropped.
        keyCache.clear()
        keyCache.putAll(next)
        keysLoadedAt = System.currentTimeMillis()
    }
}

@Serializable
private data class RemoteJwks(val keys: List<RemoteJwk>)

@Serializable
private data class RemoteJwk(
    val kty: String,
    val kid: String,
    val n: String,
    val e: String,
    val alg: String? = null,
    @SerialName("use") val use: String? = null,
)

private fun RemoteJwk.toPublicKey(): RSAPublicKey {
    val modulus = BigInteger(1, Base64.getUrlDecoder().decode(n))
    val exponent = BigInteger(1, Base64.getUrlDecoder().decode(e))
    return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent)) as RSAPublicKey
}
