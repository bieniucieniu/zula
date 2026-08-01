package com.zula.app

import com.zula.core.database.DatabaseConfig
import com.zula.core.database.databaseModule
import com.zula.core.openapi.openApiModule
import com.zula.core.security.*
import com.zula.core.serialization.serializationModule
import com.zula.core.storage.ObjectStorageConfig
import com.zula.core.storage.objectStorageModule
import com.zula.features.auth.AuthSettings
import com.zula.features.auth.authModule
import com.zula.features.chat.chatModule
import com.zula.features.feed.feedModule
import com.zula.features.geolocation.geolocationModule
import com.zula.features.groups.groupModule
import com.zula.features.media.mediaModule
import com.zula.features.moderation.moderationModule
import com.zula.features.trade.tradeModule
import com.zula.features.user.userModule
import com.zula.features.validation.validationModule
import com.zula.lib.utils.configOrNull
import com.zula.lib.utils.stringOrNull
import io.ktor.server.application.*
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger(level = Level.DEBUG)
        modules(
            databaseModule {
                runCatching {
                    log.info("database config: ${environment.config.configOrNull("database")?.toMap().toString()}")
                }.onFailure { log.error(it.message) }
                config = DatabaseConfig.from(environment.config.configOrNull("database"))
            },
            securityModule {
                val security = environment.config.configOrNull("security")
                appUrl = security?.stringOrNull("appUrl")
                jwt = JwtConfig.from(security?.configOrNull("jwt"))
                oauth = OAuthConfig(
                    google = GoogleOAuthConfig.from(security?.configOrNull("oauth.google")),
                    apple = AppleOAuthConfig.from(security?.configOrNull("oauth.apple"))
                )
                cookies = CookieConfig.from(security?.configOrNull("cookies"))
                devAuth = DevAuthConfig.from(security?.configOrNull("devAuth"))
            },
            module {
                single {
                    val security = environment.config.configOrNull("security")
                    AuthSettings.from(security?.configOrNull("auth"))
                }
            },
            serializationModule(
                environment.config.propertyOrNull("ktor.development")?.getString()?.toBooleanStrictOrNull() ?: false
            ),
            objectStorageModule {
                config = ObjectStorageConfig.from(environment.config.configOrNull("storage"))
            },
            authModule,
            // unimplemented
            openApiModule,
            userModule,
            groupModule,
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
