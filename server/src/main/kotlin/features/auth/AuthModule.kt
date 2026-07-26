package com.zula.features.auth

import com.zula.core.security.JwtSessionValidator
import com.zula.core.security.SecurityConfig
import com.zula.features.auth.crypto.TokenEncryption
import com.zula.features.auth.persistence.AuthRepository
import com.zula.features.auth.provider.AppleAuthProvider
import com.zula.features.auth.provider.AuthProviders
import com.zula.features.auth.provider.DevAuthProvider
import com.zula.features.auth.provider.GoogleAuthProvider
import com.zula.features.auth.provider.GoogleOAuthClient
import io.ktor.client.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val authModule = module {
    singleOf(::AuthRepository)
    single<TokenEncryption?> {
        val jwtConfig: SecurityConfig = get()
        val authSettings: AuthSettings = get()
        val encryption = TokenEncryption.from(jwtConfig.jwt.providerTokenEncryptionKey)
        if (authSettings.requireProviderRefreshOnLogin && encryption == null) {
            error(
                "PROVIDER_TOKEN_ENCRYPTION_KEY is required when AUTH_REQUIRE_PROVIDER_REFRESH_ON_LOGIN=true",
            )
        }
        encryption
    }
    single<GoogleOAuthClient?> {
        val security: SecurityConfig = get()
        val google = security.oauth.google ?: return@single null
        val http: HttpClient = get()
        val json: Json = get()
        GoogleOAuthClient(google, http, json)
    }
    singleOf(::ProviderTokenService)

    single<AuthProviders> {
        val http: HttpClient = get()
        val security: SecurityConfig = get()
        val json: Json = get()

        AuthProviders {
            security.oauth.google?.let { google ->
                put("google", GoogleAuthProvider(google, http, json))
            }

            val authSettings: AuthSettings = get()
            security.oauth.apple?.takeIf { authSettings.appleCodeFlowEnabled }?.let { apple ->
                put("apple", AppleAuthProvider(apple, http, json))
            }

            val devAuth = security.devAuth
            if (devAuth != null) {
                put(
                    "dev",
                    DevAuthProvider(
                        secret = devAuth.secret,
                        defaultEmail = devAuth.defaultEmail,
                    ),
                )
            }
        }
    }

    singleOf(::AuthService)

    single<JwtSessionValidator> {
        val repo: AuthRepository = get()
        JwtSessionValidator { sessionId, userId ->
            repo.findActiveSessionOwnedBy(sessionId, userId) != null
        }
    }
}
