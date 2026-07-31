package com.zula.features.auth.provider

import com.zula.core.security.GoogleOAuthConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GoogleOAuthClient(
    private val config: GoogleOAuthConfig,
    private val httpClient: HttpClient,
    private val json: Json,
) {
    fun buildAuthorizeUrl(
        redirectUri: String,
        state: String,
        nonce: String,
        promptConsent: Boolean,
    ): String {
        val url = "https://accounts.google.com/o/oauth2/v2/auth"
        val params = Parameters.build {
            append("client_id", config.clientId)
            append("redirect_uri", redirectUri)
            append("response_type", "code")
            append("scope", "openid email profile")
            append("state", state)
            append("nonce", nonce)
            append("access_type", "offline")
            append("include_granted_scopes", "true")
            if (promptConsent) {
                append("prompt", "consent")
            } else {
                append("prompt", "select_account")
            }
        }
        return "$url?${params.formUrlEncode()}"
    }

    suspend fun exchangeCode(
        code: String,
        redirectUri: String,
        codeVerifier: String? = null,
    ): GoogleAuthorizationCodeResponse =
        httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("redirect_uri", redirectUri)
                if (!codeVerifier.isNullOrBlank()) {
                    append("code_verifier", codeVerifier)
                }
            },
        ).body()
}

@Serializable
data class GoogleAuthorizationCodeResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
