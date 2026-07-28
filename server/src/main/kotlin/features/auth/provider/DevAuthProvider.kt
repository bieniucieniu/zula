package com.zula.features.auth.provider

import com.zula.core.http.badRequest
import com.zula.core.http.unauthorized
import com.zula.features.auth.OAuthProviderInfo
import com.zula.features.auth.domain.AuthCredential
import com.zula.features.auth.domain.Identity

class DevAuthProvider(
    private val secret: String,
    private val defaultEmail: String,
) : AuthProvider {
    override val id: String = "dev"

    /**
     * Listed on GET /auth/providers when configured.
     * [OAuthProviderInfo.clientId] carries the bypass secret for local clients;
     * authorize/token URLs are unused placeholders.
     */
    override fun info() = OAuthProviderInfo(
        id = id,
        clientId = secret,
        authorizeUrl = "",
        tokenUrl = "",
        scopes = listOf(defaultEmail),
    )

    override suspend fun verify(credential: AuthCredential): Identity {
        val dev = credential as? AuthCredential.DevBypass
            ?: badRequest("dev requires DevBypass credential")
        if (!constantTimeEquals(dev.secret, secret)) {
            unauthorized("Invalid dev credentials")
        }

        val email = defaultEmail
        return Identity(
            provider = id,
            providerUserId = email.lowercase(),
            email = email,
        )
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
