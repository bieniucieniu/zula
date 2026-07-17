package com.zula.core.database

import app.cash.sqldelight.db.QueryResult
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

    if (config.autoMigrate) {
        val driver: SqlDriver = get()
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

internal fun migrateSchema(driver: SqlDriver) {
    ensureUuidV7Function(driver)
    ensureSchemaVersionTable(driver)
    val target = Database.Schema.version
    val current = readSchemaVersion(driver)

    when {
        current == 0L -> {
            // .sq CREATE TABLE lives in Schema.create; migrate() is empty without .sqm files.
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

private fun ensureUuidV7Function(driver: SqlDriver) {
    driver.execute(
        null,
        // language=PostgreSQL
        """
        CREATE OR REPLACE FUNCTION generate_uuid_v7()
        RETURNS uuid
        LANGUAGE plpgsql
        AS $$
        DECLARE
          unix_ts_ms BIGINT;
          uuid_bytes BYTEA;
        BEGIN
          unix_ts_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
          uuid_bytes :=
            substring(int8send(unix_ts_ms) FROM 3 FOR 6)
            || substring(uuid_send(gen_random_uuid()) FROM 7 FOR 10);
          uuid_bytes := set_byte(uuid_bytes, 6, (get_byte(uuid_bytes, 6) & 15) | 112);
          uuid_bytes := set_byte(uuid_bytes, 8, (get_byte(uuid_bytes, 8) & 63) | 128);
          RETURN encode(uuid_bytes, 'hex')::uuid;
        END;
        $$
        """.trimIndent(),
        0,
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
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
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
        auth_challengesAdapter = Auth_challenges.Adapter(
            idAdapter = KotlinUuidAdapter
        ),
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
            rotated_from_idAdapter = KotlinUuidAdapter,
        ),
        user_statsAdapter = User_stats.Adapter(
            user_idAdapter = KotlinUuidAdapter
        ),
        usersAdapter = Users.Adapter(
            idAdapter = KotlinUuidAdapter
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
