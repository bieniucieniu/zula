package com.zula.features.auth.provider

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class OidcIdTokenVerifier(
    private val httpClient: HttpClient,
    private val jwksUrl: String,
    private val issuers: List<String>,
    private val audience: String,
) {
    constructor(
        httpClient: HttpClient,
        jwksUrl: String,
        issuer: String,
        audience: String,
    ) : this(httpClient, jwksUrl, listOf(issuer), audience)

    private val json = Json { ignoreUnknownKeys = true }
    private val keyCache = ConcurrentHashMap<String, Algorithm>()
    private var keysLoadedAt = 0L

    suspend fun verify(idToken: String): DecodedJWT {
        refreshKeysIfNeeded()
        val decoded = JWT.decode(idToken)
        val kid = decoded.keyId ?: error("ID token missing kid")
        val algorithm = keyCache[kid] ?: run {
            refreshKeys(force = true)
            keyCache[kid] ?: error("Unknown key id: $kid")
        }
        val verifier = JWT.require(algorithm)
            .withIssuer(*issuers.toTypedArray())
            .withAudience(audience)
            .build()
        return verifier.verify(decoded)
    }

    private suspend fun refreshKeysIfNeeded() {
        if (keyCache.isEmpty() || System.currentTimeMillis() - keysLoadedAt > 3_600_000) {
            refreshKeys(force = true)
        }
    }

    private suspend fun refreshKeys(force: Boolean) {
        if (!force && keyCache.isNotEmpty()) return
        val remote: String = httpClient.get(jwksUrl).body()
        val jwks: RemoteJwks = json.decodeFromString(remote)
        jwks.keys.forEach { key ->
            if (key.kty == "RSA") {
                keyCache[key.kid] = Algorithm.RSA256(key.toPublicKey(), null)
            }
        }
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
