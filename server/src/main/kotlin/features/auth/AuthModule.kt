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
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val authModule = module {
    singleOf(::AuthRepository)
    single {
        val jwtConfig: SecurityConfig = get()
        TokenEncryption(jwtConfig.jwt.providerTokenEncryptionKey)
    }
    single {
        val security: SecurityConfig = get()
        val http: HttpClient = get()
        val json: Json = get()
        GoogleOAuthClient(security.oauth.google, http, json)
    }
    singleOf(::ProviderTokenService)

    single<AuthProviders> {
        val http: HttpClient = get()
        val security: SecurityConfig = get()
        val json: Json = get()

        AuthProviders {
            if (security.oauth.google.isIdTokenConfigured || security.oauth.google.isConfigured) {
                put("google", GoogleAuthProvider(security.oauth.google, http, json))
            }

            val authSettings: AuthSettings = get()
            if (security.oauth.apple.isIdTokenConfigured && authSettings.appleCodeFlowEnabled) {
                put("apple", AppleAuthProvider(security.oauth.apple, http, json))
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
