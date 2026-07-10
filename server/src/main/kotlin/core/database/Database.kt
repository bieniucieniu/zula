package com.zula.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariDataSource
import com.zula.Database
import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.ext.getKoin

fun Application.configureDatabase() {
    val config = getKoin().getOrNull<DatabaseConfig>()
    if (config == null) {
        log.info("Database disabled, no JDBC URL provided")
        return
    }

    if (config.autoMigrate) {
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

fun databaseModule(builder: DatabaseConfigBuilder.() -> Unit): Module =
    databaseModule(DatabaseConfigBuilder().apply(builder).build())

fun databaseModule(config: DatabaseConfig): Module = module {
    if (!config.isEnabled) {
        return@module
    }

    single { config }

    single<HikariDataSource> {
        HikariDataSource(config.toHikariConfig())
    }

    single<JdbcDriver> {
        val h: HikariDataSource = get()
        h.asJdbcDriver()
    }

    single<Database> {
        Database(get())
    }
}
