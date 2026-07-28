package com.zula.features.auth

import kotlinx.serialization.Serializable

@Serializable
data class OAuthProvidersResponse(
    val providers: List<OAuthProviderInfo>,
)

@Serializable
data class OAuthProviderInfo(
    val id: String,
    val clientId: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    val scopes: List<String>,
)
