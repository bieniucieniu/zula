package com.zula.core.database

import io.ktor.server.application.*
import org.koin.dsl.module

fun Application.configureDatabase() {
    // SQLDelight + HikariCP + migration runner — wire when core/database ships
}

val databaseModule = module {
    // single { DatabaseFactory(environment) }
}
