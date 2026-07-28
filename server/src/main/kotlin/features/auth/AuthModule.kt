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

    /**
     * Build [ProviderTokenService] / [AuthService] explicitly.
     * Koin `single { … }` must not return null — nullable `GoogleOAuthClient?` /
     * `TokenEncryption?` bindings blew up with
     * "Single instance created couldn't return value" when Google (or encryption) was unset.
     */
    single {
        val security: SecurityConfig = get()
        val authSettings: AuthSettings = get()
        val encryption = TokenEncryption.from(security.jwt.providerTokenEncryptionKey)
        if (authSettings.requireProviderRefreshOnLogin && encryption == null) {
            error(
                "PROVIDER_TOKEN_ENCRYPTION_KEY is required when AUTH_REQUIRE_PROVIDER_REFRESH_ON_LOGIN=true",
            )
        }
        ProviderTokenService(
            repository = get(),
            encryption = encryption,
            securityConfig = security,
            authSettings = authSettings,
            httpClient = get(),
            json = get(),
        )
    }

    single {
        val security: SecurityConfig = get()
        val http: HttpClient = get()
        val json: Json = get()
        AuthService(
            repository = get(),
            providers = get(),
            jwtIssuer = get(),
            securityConfig = security,
            authSettings = get(),
            providerTokenService = get(),
            googleOAuthClient = security.oauth.google?.let { GoogleOAuthClient(it, http, json) },
            json = json,
        )
    }

    single<JwtSessionValidator> {
        val repo: AuthRepository = get()
        JwtSessionValidator { sessionId, userId ->
            repo.findActiveSessionOwnedBy(sessionId, userId) != null
        }
    }
}
