package com.zula.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariDataSource
import com.zula.*
import com.zula.lib.id.KotlinUuidAdapter
import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.ext.getKoin

fun Application.configureDatabase() {
    val config: DatabaseConfig? = getKoin().getOrNull()
    if (config == null) {
        log.info("Database disabled, no JDBC URL provided")
        return
    }

    val driver: SqlDriver = get()
    requirePostgres18OrNewer(driver)

    if (config.autoMigrate) {
        migrateSchema(driver)
        log.info("Database schema at version ${Database.Schema.version}")
    } else {
        log.info("Database auto-migrate disabled (AUTO_MIGRATE=false)")
    }

    val database: Database = get()
    database.healthQueries.healthCheck().executeAsOne()
    log.info("Database health check OK")

    val dataSource: HikariDataSource = get()
    monitor.subscribe(ApplicationStopping) {
        dataSource.close()
    }
}

/**
 * Applies SQLDelight `.sqm` migrations. Table DDL lives in `0.sqm` (init).
 * Only [zula_schema_version] is created here — not via `.sqm`.
 */
internal fun migrateSchema(driver: SqlDriver) {
    dropAuthChallengesTable(driver)
    ensureSchemaVersionTable(driver)
    val target = Database.Schema.version
    val current = readSchemaVersion(driver)

    when {
        current < 0 -> {
            // Fresh DB: Schema.create runs init migration(s) derived from .sqm files.
            Database.Schema.create(driver)
            writeSchemaVersion(driver, target)
        }

        current < target -> {
            Database.Schema.migrate(
                driver = driver,
                oldVersion = current,
                newVersion = target,
            )
            writeSchemaVersion(driver, target)
        }
    }
}

private fun dropAuthChallengesTable(driver: SqlDriver) {
    driver.execute(
        identifier = null,
        sql = "DROP TABLE IF EXISTS auth_challenges",
        parameters = 0,
    )
}

private fun ensureSchemaVersionTable(driver: SqlDriver) {
    driver.execute(
        null,
        // language=PostgreSQL
        """
        CREATE TABLE IF NOT EXISTS zula_schema_version (
            version BIGINT NOT NULL
        )
        """.trimIndent(),
        0,
    )
}

private fun readSchemaVersion(driver: SqlDriver): Long {
    return driver.executeQuery(
        null,
        // language=PostgreSQL
        "SELECT version FROM zula_schema_version LIMIT 1",
        { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: -1L else -1L)
        },
        0,
    ).value
}

private fun writeSchemaVersion(driver: SqlDriver, version: Long) {
    driver.execute(
        null,
        // language=PostgreSQL
        "DELETE FROM zula_schema_version",
        0
    )
    driver.execute(
        null,
        // language=PostgreSQL
        "INSERT INTO zula_schema_version (version) VALUES (?)",
        1
    ) {
        bindLong(0, version)
    }
}

fun createDatabase(driver: SqlDriver): Database =
    Database(
        driver = driver,
        user_identitiesAdapter = User_identities.Adapter(
            idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
        ),
        user_profilesAdapter = User_profiles.Adapter(
            user_idAdapter = KotlinUuidAdapter
        ),
        user_sessionsAdapter = User_sessions.Adapter(
            idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
            identity_idAdapter = KotlinUuidAdapter,
            rotated_from_idAdapter = KotlinUuidAdapter,
        ),
        user_statsAdapter = User_stats.Adapter(
            user_idAdapter = KotlinUuidAdapter
        ),
        usersAdapter = Users.Adapter(
            idAdapter = KotlinUuidAdapter
        ),
        user_blocksAdapter = User_blocks.Adapter(
            blocker_idAdapter = KotlinUuidAdapter,
            blocked_idAdapter = KotlinUuidAdapter,
        ),
        user_ratingsAdapter = User_ratings.Adapter(
            idAdapter = KotlinUuidAdapter,
            reviewer_idAdapter = KotlinUuidAdapter,
            reviewee_idAdapter = KotlinUuidAdapter,
        ),
        seller_activity_statsAdapter = Seller_activity_stats.Adapter(
            user_idAdapter = KotlinUuidAdapter,
        ),
    )

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

    single<SqlDriver> {
        val h: HikariDataSource = get()
        h.asJdbcDriver()
    }

    single<Database> {
        val driver: SqlDriver = get()
        createDatabase(driver)
    }
}
