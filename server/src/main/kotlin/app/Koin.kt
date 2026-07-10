package com.zula.app

import com.zula.core.database.databaseModule
import com.zula.core.openapi.openApiModule
import com.zula.core.rabbitmq.rabbitmqModule
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
            securityModule(this@configureKoin),
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
