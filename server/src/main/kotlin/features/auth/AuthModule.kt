package com.zula.features.auth

import com.zula.core.security.JwtSessionValidator
import com.zula.core.security.SecurityConfig
import com.zula.features.auth.crypto.TokenEncryption
import com.zula.features.auth.persistence.AuthRepository
import com.zula.features.auth.provider.*
import io.ktor.client.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val authModule = module {
    singleOf(::AuthRepository)
    single {
        val jwtConfig: SecurityConfig = get()
        TokenEncryption(jwtConfig.jwt.providerTokenEncryptionKey)
    }
    singleOf(::ProviderTokenService)

    single<AuthProviders> {
        val http: HttpClient = get()
        val security: SecurityConfig = get()
        val repo: AuthRepository = get()
        val json: Json = get()

        AuthProviders {
            if (security.oauth.google.isIdTokenConfigured) {
                put("google", GoogleAuthProvider(security.oauth.google, http, json))
            }

            if (security.oauth.apple.isIdTokenConfigured) {
                put("apple", AppleAuthProvider(security.oauth.apple, http, json))
            }

            val emailOtp = EmailOtpAuthProvider(repo)
            put("email_otp", emailOtp)
            put("email", emailOtp)
            put("phone", emailOtp)

            val magicLink = MagicLinkAuthProvider(repo)
            put("magic_link", magicLink)

            val devAuth = security.devAuth
            if (devAuth.enabled) {
                put(
                    "dev",
                    DevAuthProvider(
                        secret = devAuth.secret!!,
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
