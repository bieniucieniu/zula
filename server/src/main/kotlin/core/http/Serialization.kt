package com.zula.core.http

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    val pp =
        environment.config.propertyOrNull("ktor.development")?.getString()?.toBooleanStrictOrNull() ?: true
    install(ContentNegotiation) {
        json(Json {
            isLenient = true
            prettyPrint = pp
        })
    }
}
