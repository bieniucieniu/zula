package com.zula.core.http

import com.zula.lib.utils.toBooleanOrNull
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    val pp = environment.config.propertyOrNull("ktor.development")?.toBooleanOrNull() ?: true
    install(ContentNegotiation) {
        json(Json {
            isLenient = true
            prettyPrint = pp
        })
    }
}
