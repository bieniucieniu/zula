package com.zula.features.auth

import com.zula.core.security.IssuedSessionToken
import com.zula.core.security.JwtSessionService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.HexFormat

class AuthService(
    private val jwtSessionService: JwtSessionService,
    private val httpClient: HttpClient = HttpClient(Apache5),
) {
    fun listProviders(): List<AuthProviderInfo> =
        listOf(
            AuthProviderInfo(
                id = "google",
                name = "Google",
                supportsIdToken = true,
                supportsOAuthRedirect = true,
            ),
        )

    suspend fun authenticateWithIdToken(
        provider: String,
        idToken: String,
    ): IssuedSessionToken {
        require(provider == "google") { "Unsupported provider: $provider" }
        require(idToken.isNotBlank()) { "idToken is required" }

        val subject = verifyGoogleIdTokenPresence(idToken)
        return issueSession(subject)
    }

    suspend fun authenticateWithOAuthAccessToken(accessToken: String): IssuedSessionToken {
        require(accessToken.isNotBlank()) { "accessToken is required" }

        val subject = fetchGoogleSubject(accessToken)
        return issueSession(subject)
    }

    fun issueSession(subject: String): IssuedSessionToken = jwtSessionService.issueToken(subject)

    private suspend fun fetchGoogleSubject(accessToken: String): String {
        val profile: GoogleUserInfo =
            httpClient
                .get("https://www.googleapis.com/oauth2/v3/userinfo") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }.body()

        return profile.sub ?: error("Google userinfo response missing subject")
    }

    private fun verifyGoogleIdTokenPresence(idToken: String): String {
        // Full Google ID token verification ships with AuthProvider; hash stub subject for now.
        val digest = MessageDigest.getInstance("SHA-256").digest(idToken.toByteArray())
        return "google:${HexFormat.of().formatHex(digest).take(32)}"
    }
}

@Serializable
data class AuthProviderInfo(
    val id: String,
    val name: String,
    val supportsIdToken: Boolean,
    val supportsOAuthRedirect: Boolean,
)

@Serializable
data class AuthenticateRequest(
    val provider: String,
    val idToken: String,
)

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)

@Serializable
private data class GoogleUserInfo(
    val sub: String? = null,
    val email: String? = null,
)

fun IssuedSessionToken.toResponse(): AuthTokenResponse =
    AuthTokenResponse(
        accessToken = accessToken,
        tokenType = tokenType,
        expiresIn = expiresInSeconds,
    )
