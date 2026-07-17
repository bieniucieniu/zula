package com.zula.features.auth.provider

import com.zula.features.auth.OAuthProviderInfo
import com.zula.features.auth.domain.AuthCredential
import com.zula.features.auth.domain.Identity

interface AuthProvider {
    val id: String
    fun info(): OAuthProviderInfo?
    suspend fun verify(credential: AuthCredential): Identity
}

class AuthProviders(private val p: Map<String, AuthProvider>) : Map<String, AuthProvider> by p {
    constructor(builder: MutableMap<String, AuthProvider>.() -> Unit) : this(buildMap(builder))
}
