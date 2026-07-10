package com.zula.app

import com.zula.core.database.DatabaseConfig
import com.zula.core.database.databaseModule
import com.zula.core.openapi.openApiModule
import com.zula.core.rabbitmq.rabbitmqModule
import com.zula.core.security.JwtConfig
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
import com.zula.lib.utils.stringOrNull
import com.zula.lib.utils.configOrNull
import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(
            databaseModule {
                config = environment.config.configOrNull("database")?.let { DatabaseConfig(it) } ?: DatabaseConfig()
            },
            securityModule {
                val security = environment.config.configOrNull("security")
                appUrl = security?.stringOrNull("appUrl")
                jwt = security?.configOrNull("jwt")?.let { JwtConfig(it) } ?: JwtConfig()
                oauth = security?.configOrNull("oauth")?.let { OAuthConfig(it) } ?: OAuthConfig()
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
