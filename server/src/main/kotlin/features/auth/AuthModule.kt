package com.zula.features.auth

import com.zula.core.security.JwtSessionValidator
import com.zula.core.security.SecurityConfig
import com.zula.features.auth.crypto.TokenEncryption
import com.zula.features.auth.persistence.AuthRepository
import com.zula.features.auth.provider.*
import io.ktor.client.*
import org.koin.dsl.module

val authModule = module {
    single {
        AuthRepository(get())
    }
    single {
        val jwtConfig: SecurityConfig = get()
        TokenEncryption(jwtConfig.jwt.providerTokenEncryptionKey)
    }
    single {
        val security: SecurityConfig = get()

        ProviderTokenService(
            googleConfig = security.oauth.google,
            repository = get(),
            encryption = get(),
            httpClient = get(),
        )
    }

    single<AuthProviders> {
        val http: HttpClient = get()
        val security: SecurityConfig = get()
        val repo: AuthRepository = get()

        AuthProviders {
            if (security.oauth.google.isConfigured) {
                put("google", GoogleAuthProvider(security.oauth.google, http))
            }

            if (security.oauth.apple.isConfigured) {
                put("apple", AppleAuthProvider(security.oauth.apple, http))
            }

            val emailOtp = EmailOtpAuthProvider(repo)
            put("email_otp", emailOtp)
            put("email", emailOtp)
            put("phone", emailOtp)

            val magicLink = MagicLinkAuthProvider(repo)
            put("magic_link", magicLink)
        }
    }

    single {
        AuthService(
            get(),
            get(),
            get(),
            get<SecurityConfig>().jwt,
            get()
        )
    }

    single<JwtSessionValidator> {
        val repo: AuthRepository = get()
        JwtSessionValidator { sessionId, userId ->
            repo.findActiveSessionOwnedBy(sessionId, userId) != null
        }
    }
}
