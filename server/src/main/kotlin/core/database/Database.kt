package com.zula.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zula.Database
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.inject

private fun ApplicationConfig.databaseConfig(): Map<String, Any?>? =
    propertyOrNull("database")?.getMap()

private fun Map<String, Any?>.jdbcUrl(): String =
    get("jdbcUrl")?.toString().orEmpty()

private fun Map<String, Any?>.autoMigrateEnabled(): Boolean =
    when (get("autoMigrate")?.toString()?.lowercase()) {
        "false", "0", "no" -> false
        else -> true
    }

fun Application.configureDatabase() {
    val database = environment.config.databaseConfig()
    if (database?.jdbcUrl().isNullOrBlank()) {
        log.info("Database disabled, no JDBC URL provided")
        return
    }

    if (database.autoMigrateEnabled()) {
        val driver: SqlDriver by inject()
        Database.Schema.migrate(
            driver = driver,
            oldVersion = 0,
            newVersion = Database.Schema.version,
        )
        log.info("Database schema migrated to version ${Database.Schema.version}")
    } else {
        log.info("Database auto-migrate disabled (AUTO_MIGRATE=false)")
    }

    val db: Database by inject()
    db.healthQueries.healthCheck().executeAsOne()
    log.info("Database health check OK")

    val dataSource: HikariDataSource by inject()
    monitor.subscribe(ApplicationStopping) {
        dataSource.close()
    }
}

fun databaseModule(database: Map<String, Any?>?): Module = module {
    val config = database ?: return@module
    val jdbcUrl = config.jdbcUrl()
    if (jdbcUrl.isBlank()) {
        return@module
    }

    single<HikariDataSource> {
        HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = config["username"]?.toString() ?: "zula"
                password = config["password"]?.toString() ?: "zula"
                maximumPoolSize = config["maximumPoolSize"]?.toString()?.toInt() ?: 10
                driverClassName = "org.postgresql.Driver"
            },
        )
    }

    single<SqlDriver> {
        get<HikariDataSource>().asJdbcDriver()
    }

    single {
        Database(get<SqlDriver>())
    }
}
