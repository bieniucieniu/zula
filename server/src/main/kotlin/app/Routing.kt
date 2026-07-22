package com.zula.app

import com.zula.core.security.jwt.configureJwksRouting
import com.zula.features.auth.configureAuthRouting
import com.zula.features.chat.configureChatRouting
import com.zula.features.feed.configureFeedRouting
import com.zula.features.geolocation.configureGeolocationRouting
import com.zula.features.media.configureMediaRouting
import com.zula.features.moderation.configureModerationRouting
import com.zula.features.sync.configureSyncRouting
import com.zula.features.trade.configureTradeRouting
import com.zula.features.user.configureUserRouting
import com.zula.features.validation.configureValidationRouting
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        configureJwksRouting()

        route("/api") {
            configureAuthRouting()
            configureSyncRouting()
            configureUserRouting()
            configureFeedRouting()
            configureMediaRouting()
            configureGeolocationRouting()
            configureTradeRouting()
            configureValidationRouting()
            configureChatRouting()
            configureModerationRouting()
        }
    }
}
