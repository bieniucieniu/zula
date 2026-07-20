package com.zula.core.health

import com.zula.Database
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.getKoin

fun Application.configureHealth() {
    routing {
        get("/health") {
            val database: Database? = getKoin().getOrNull()
            if (database == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("status" to "unavailable", "database" to "disabled"),
                )
                return@get
            }

            try {
                database.healthQueries.healthCheck().executeAsOne()
                call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            } catch (e: Exception) {
                call.application.log.warn("Health check failed", e)
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("status" to "unavailable", "database" to "down"),
                )
            }
        }
    }
}
