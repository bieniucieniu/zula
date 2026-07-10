package com.zula.core.database

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zula.Database
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.inject

fun Application.configureDatabase() {
    val jdbcUrl = environment.config.propertyOrNull("database.jdbcUrl")?.getString()
    if (jdbcUrl.isNullOrBlank()) {
        log.info("Database disabled, no JDBC URL provided")
        return
    }

    val database: Database by inject()
    database.healthQueries.healthCheck().executeAsOne()
    log.info("Database health check OK")

    val dataSource: HikariDataSource by inject()
    monitor.subscribe(ApplicationStopping) {
        dataSource.close()
    }
}

fun databaseModule(application: Application): Module = module {
    val config = application.environment.config
    val jdbcUrl = config.propertyOrNull("database.jdbcUrl")?.getString()
    if (jdbcUrl.isNullOrBlank()) {
        return@module
    }

    single<HikariDataSource> {
        HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = config.property("database.username").getString()
                password = config.property("database.password").getString()
                maximumPoolSize = config.property("database.maximumPoolSize").getString().toInt()
                driverClassName = "org.postgresql.Driver"
            },
        )
    }

    single {
        Database(get<HikariDataSource>().asJdbcDriver())
    }
}
