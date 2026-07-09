package com.zula.core.openapi

import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import org.koin.dsl.module

fun Application.configureOpenApi() {
    routing {
        openAPI(path = "openapi") {
            // Served from documentation.yaml or route describe {} metadata
        }
    }
    routing {
        swaggerUI(path = "swagger") {
            // Swagger UI at /swagger per architecture
        }
    }
}

val openApiModule = module {
    // Shared DTOs (FeedCursor, ProfileCursor, …) when core/openapi ships
}
