package com.zula.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zula.Database
import com.zula.lib.utils.toBooleanOrNull
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.get

private fun ApplicationConfig.databaseConfig(): Map<String, Any?>? =
    propertyOrNull("database")?.getMap()

private fun Map<String, Any?>.jdbcUrl(): String =
    get("jdbcUrl")?.toString().orEmpty()

private fun Map<String, Any?>.autoMigrateEnabled(): Boolean = get("autoMigrate")?.toBooleanOrNull() ?: true


fun Application.configureDatabase() {
    val database = environment.config.databaseConfig()
    if (database?.jdbcUrl().isNullOrBlank()) {
        log.info("Database disabled, no JDBC URL provided")
        return
    }

    if (database.autoMigrateEnabled()) {
        Database.Schema.migrate(
            driver = get<SqlDriver>(),
            oldVersion = 0,
            newVersion = Database.Schema.version,
        )
        log.info("Database schema migrated to version ${Database.Schema.version}")
    } else {
        log.info("Database auto-migrate disabled (AUTO_MIGRATE=false)")
    }

    get<Database>().healthQueries.healthCheck().executeAsOne()
    log.info("Database health check OK")

    val dataSource = get<HikariDataSource>()
    monitor.subscribe(ApplicationStopping) {
        dataSource.close()
    }
}

fun databaseModule(builder: HikariConfig.() -> Unit): Module = databaseModule(HikariConfig().apply(builder))
fun databaseModule(config: HikariConfig): Module = module {
    if (config.jdbcUrl.isBlank()) {
        return@module
    }


    single<HikariDataSource> {
        HikariDataSource(
            config.apply {
                driverClassName = "org.postgresql.Driver"
            },
        )
    }

    single<JdbcDriver> {
        val h: HikariDataSource = get()
        h.asJdbcDriver()
    }

    single<Database> {
        Database(get())
    }
}
