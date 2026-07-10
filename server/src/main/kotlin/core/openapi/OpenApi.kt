package com.zula.core.openapi

import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import org.koin.dsl.module

fun Application.configureOpenApi() {
    routing {
        swaggerUI(path = "swagger") {
            // Swagger UI at /swagger per architecture
        }
    }
}

val openApiModule = module {
    // Shared DTOs (FeedCursor, ProfileCursor, …) when core/openapi ships
}
