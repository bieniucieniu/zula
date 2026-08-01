package com.zula.app

import com.zula.core.http.ProblemDetails
import com.zula.core.jobrunr.configureJobRunrRouting
import com.zula.core.security.jwt.configureJwksRouting
import com.zula.features.auth.configureAuthRouting
import com.zula.features.chat.configureChatRouting
import com.zula.features.feed.configureFeedRouting
import com.zula.features.geolocation.configureGeolocationRouting
import com.zula.features.groups.configureGroupRouting
import com.zula.features.media.configureMediaRouting
import com.zula.features.moderation.configureModerationRouting
import com.zula.features.trade.configureTradeRouting
import com.zula.features.user.configureUserRouting
import com.zula.features.validation.configureValidationRouting
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*

fun Application.configureRouting() {
    routing {
        configureJwksRouting()
        configureJobRunrRouting()

        route("/api") {
            configureAuthRouting()
            configureUserRouting()
            configureFeedRouting()
            configureGroupRouting()
            configureMediaRouting()
            configureGeolocationRouting()
            configureTradeRouting()
            configureValidationRouting()
            configureChatRouting()
            configureModerationRouting()
        }.describe {
            responses {
                HttpStatusCode.InternalServerError {
                    schema = jsonSchema<ProblemDetails>()
                }
            }
        }
    }
}
