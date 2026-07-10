package com.zula.app

import com.zula.core.database.databaseModule
import com.zula.core.openapi.openApiModule
import com.zula.core.rabbitmq.rabbitmqModule
import com.zula.core.security.AppleOAuthConfig
import com.zula.core.security.GoogleOAuthConfig
import com.zula.core.security.JwtConfig
import com.zula.core.security.JwtKubernetesConfig
import com.zula.core.security.OAuthConfig
import com.zula.core.security.securityModule
import com.zula.features.auth.authModule
import com.zula.features.chat.chatModule
import com.zula.features.feed.feedModule
import com.zula.features.geolocation.geolocationModule
import com.zula.features.media.mediaModule
import com.zula.features.moderation.moderationModule
import com.zula.features.trade.tradeModule
import com.zula.features.user.userModule
import com.zula.features.validation.validationModule
import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(
            databaseModule {
                val config = environment.config.propertyOrNull("database")?.getMap() ?: return@databaseModule
                jdbcUrl = config["jdbcUrl"]?.toString()
                username = config["username"]?.toString()
                password = config["password"]?.toString()
                maximumPoolSize = config["maximumPoolSize"]?.toString()?.toInt() ?: 10
            },
            securityModule {
                val security = environment.config.config("security")
                val jwtConfig = security.config("jwt")
                val kubernetesConfig = jwtConfig.config("kubernetes")
                val oauthConfig = security.config("oauth")
                val googleConfig = oauthConfig.config("google")
                val appleConfig = oauthConfig.config("apple")

                appUrl = security.propertyOrNull("appUrl")?.getString()?.takeIf { it.isNotBlank() }
                jwt = JwtConfig(
                    audience = jwtConfig.propertyOrNull("audience")?.getString() ?: "zula",
                    realm = jwtConfig.propertyOrNull("realm")?.getString() ?: "Zula",
                    autoGenerateKey = jwtConfig.propertyOrNull("autoGenerateKey")?.getString()?.toBooleanStrictOrNull()
                        ?: true,
                    privateKeyPem = jwtConfig.propertyOrNull("privateKeyPem")?.getString()?.takeIf { it.isNotBlank() },
                    publicKeyPem = jwtConfig.propertyOrNull("publicKeyPem")?.getString()?.takeIf { it.isNotBlank() },
                    accessTokenTtlSeconds = jwtConfig.propertyOrNull("accessTokenTtlSeconds")?.getString()?.toLongOrNull()
                        ?: 3600,
                    kubernetes = JwtKubernetesConfig(
                        enabled = kubernetesConfig.propertyOrNull("enabled")?.getString()?.toBooleanStrictOrNull()
                            ?: false,
                        namespace = kubernetesConfig.propertyOrNull("namespace")?.getString()?.takeIf { it.isNotBlank() },
                        secretName = kubernetesConfig.propertyOrNull("secretName")?.getString()?.takeIf { it.isNotBlank() }
                            ?: "zula-jwt-keys",
                        autoPull = kubernetesConfig.propertyOrNull("autoPull")?.getString()?.toBooleanStrictOrNull()
                            ?: true,
                        autoPush = kubernetesConfig.propertyOrNull("autoPush")?.getString()?.toBooleanStrictOrNull()
                            ?: false,
                    ),
                )
                oauth = OAuthConfig(
                    google = GoogleOAuthConfig(
                        clientId = googleConfig.propertyOrNull("clientId")?.getString()?.takeIf { it.isNotBlank() },
                        clientSecret = googleConfig.propertyOrNull("clientSecret")?.getString()?.takeIf { it.isNotBlank() },
                    ),
                    apple = AppleOAuthConfig(
                        clientId = appleConfig.propertyOrNull("clientId")?.getString()?.takeIf { it.isNotBlank() },
                        teamId = appleConfig.propertyOrNull("teamId")?.getString()?.takeIf { it.isNotBlank() },
                        keyId = appleConfig.propertyOrNull("keyId")?.getString()?.takeIf { it.isNotBlank() },
                        privateKeyPem = appleConfig.propertyOrNull("privateKeyPem")?.getString()?.takeIf { it.isNotBlank() },
                    ),
                )
            },
            openApiModule,
            rabbitmqModule,
            authModule,
            userModule,
            feedModule,
            mediaModule,
            geolocationModule,
            tradeModule,
            validationModule,
            chatModule,
            moderationModule,
        )
    }
}
