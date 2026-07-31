package com.zula.features.auth.provider

import com.zula.features.auth.OAuthProviderInfo
import com.zula.features.auth.domain.AuthCredential
import com.zula.features.auth.domain.ProviderIdentity

interface AuthProvider {
    val id: String
    fun info(): OAuthProviderInfo?
    suspend fun verify(
        idToken: String,
        providerRefreshToken: String? = null,
        scopes: String? = null,
        expectedNonce: String? = null,
    ): ProviderIdentity = verify(
        AuthCredential.OAuthIdToken(idToken, providerRefreshToken, scopes, expectedNonce)
    )

    suspend fun verify(
        secret: String,
        email: String? = null
    ): ProviderIdentity = verify(AuthCredential.DevBypass(secret, email))

    suspend fun verify(credential: AuthCredential): ProviderIdentity
}

class AuthProviders(private val p: Map<String, AuthProvider>) : Map<String, AuthProvider> by p {
    constructor(builder: MutableMap<String, AuthProvider>.() -> Unit) : this(buildMap(builder))

    fun publicInfo(): List<OAuthProviderInfo> = values.mapNotNull { it.info() }
}
