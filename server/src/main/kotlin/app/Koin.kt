package com.zula.app

import com.zula.core.database.DatabaseConfig
import com.zula.core.database.databaseModule
import com.zula.core.openapi.openApiModule
import com.zula.core.security.*
import com.zula.features.auth.authModule
import com.zula.features.chat.chatModule
import com.zula.features.feed.feedModule
import com.zula.features.geolocation.geolocationModule
import com.zula.features.media.mediaModule
import com.zula.features.moderation.moderationModule
import com.zula.features.sync.syncModule
import com.zula.features.trade.tradeModule
import com.zula.features.user.userModule
import com.zula.features.validation.validationModule
import com.zula.lib.utils.configOrNull
import com.zula.lib.utils.stringOrNull
import io.ktor.server.application.*
import org.koin.core.logger.Level
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
            },
            openApiModule,
            authModule,
            userModule,
            syncModule,
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
