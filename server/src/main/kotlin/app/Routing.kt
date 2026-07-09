package com.zula.app

import com.ucasoft.ktor.simpleCache.cacheOutput
import com.zula.features.auth.configureAuthRouting
import com.zula.features.chat.configureChatRouting
import com.zula.features.feed.configureFeedRouting
import com.zula.features.geolocation.configureGeolocationRouting
import com.zula.features.media.configureMediaRouting
import com.zula.features.moderation.configureModerationRouting
import com.zula.features.trade.configureTradeRouting
import com.zula.features.user.configureUserRouting
import com.zula.features.validation.configureValidationRouting
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting() {
    configureAuthRouting()
    configureUserRouting()
    configureFeedRouting()
    configureMediaRouting()
    configureGeolocationRouting()
    configureTradeRouting()
    configureValidationRouting()
    configureChatRouting()
    configureModerationRouting()

    routing {
        route("/api") {
            get {
                call.respondText("Hello, World!")
            }
            cacheOutput(2.seconds) {
                get("/short") {
                    call.respond(Random.nextInt().toString())
                }
            }
            cacheOutput {
                get("/default") {
                    call.respond(Random.nextInt().toString())
                }
            }
            get("/json/kotlinx-serialization") {
                call.respond(mapOf("hello" to "world"))
            }
            sse("/hello") {
                send(ServerSentEvent("world"))
            }
        }
    }
}
