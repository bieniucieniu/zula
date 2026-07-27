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

internal fun migrateSchema(driver: SqlDriver) {
    migrateLegacyUuidDefaults(driver)
    migrateAuthSessionIdentityColumn(driver)
    dropAuthChallengesTable(driver)
    ensureSchemaVersionTable(driver)
    val target = Database.Schema.version
    val current = readSchemaVersion(driver)

    when {
        current < 0 -> {
            // .sq CREATE TABLE lives in Schema.create; migrate() is empty without .sqm files.
            // If create succeeded but version stamp failed, tables remain while current stays 0.
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
    // Additive tables for DBs created before user social schema (Schema.migrate may be empty).
    ensureUserSocialTables(driver)
}

private fun migrateLegacyUuidDefaults(driver: SqlDriver) {
    val uuidPrimaryKeyTables = listOf(
        "users",
        "user_identities",
        "user_sessions",
    )

    for (table in uuidPrimaryKeyTables) {
        driver.execute(
            identifier = null,
            sql = "ALTER TABLE IF EXISTS $table ALTER COLUMN id SET DEFAULT uuidv7()",
            parameters = 0,
        )
    }

    driver.execute(
        identifier = null,
        sql = "DROP FUNCTION IF EXISTS generate_uuid_v7()",
        parameters = 0,
    )
}

private fun migrateAuthSessionIdentityColumn(driver: SqlDriver) {
    if (!relationExists(driver, "user_sessions")) return
    driver.execute(
        identifier = null,
        sql = "ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS identity_id UUID REFERENCES user_identities(id) ON DELETE SET NULL",
        parameters = 0,
    )
    driver.execute(
        identifier = null,
        sql = "CREATE INDEX IF NOT EXISTS user_sessions_identity_id ON user_sessions(identity_id)",
        parameters = 0,
    )
}

private fun dropAuthChallengesTable(driver: SqlDriver) {
    driver.execute(
        identifier = null,
        sql = "DROP TABLE IF EXISTS auth_challenges",
        parameters = 0,
    )
}

private fun ensureUserSocialTables(driver: SqlDriver) {
    if (!relationExists(driver, "users")) return
    driver.execute(
        identifier = null,
        sql = """
            CREATE TABLE IF NOT EXISTS user_blocks (
                blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                PRIMARY KEY (blocker_id, blocked_id)
            )
        """.trimIndent(),
        parameters = 0,
    )
    driver.execute(
        identifier = null,
        sql = "CREATE INDEX IF NOT EXISTS user_blocks_blocked_id ON user_blocks(blocked_id)",
        parameters = 0,
    )
    driver.execute(
        identifier = null,
        sql = """
            CREATE TABLE IF NOT EXISTS user_ratings (
                id UUID NOT NULL PRIMARY KEY DEFAULT uuidv7(),
                reviewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                reviewee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                rating BIGINT NOT NULL,
                comment TEXT,
                UNIQUE (reviewer_id, reviewee_id)
            )
        """.trimIndent(),
        parameters = 0,
    )
    driver.execute(
        identifier = null,
        sql = "CREATE INDEX IF NOT EXISTS user_ratings_reviewee_id ON user_ratings(reviewee_id, id)",
        parameters = 0,
    )
    driver.execute(
        identifier = null,
        sql = """
            CREATE TABLE IF NOT EXISTS seller_activity_stats (
                user_id UUID NOT NULL PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                active_offer_count BIGINT NOT NULL DEFAULT 0,
                active_need_count BIGINT NOT NULL DEFAULT 0,
                active_trip_count BIGINT NOT NULL DEFAULT 0,
                fulfilled_item_count BIGINT NOT NULL DEFAULT 0,
                completed_trade_count BIGINT NOT NULL DEFAULT 0,
                updated_at BIGINT NOT NULL
            )
        """.trimIndent(),
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

private fun relationExists(driver: SqlDriver, name: String): Boolean =
    driver.executeQuery(
        null,
        // language=PostgreSQL
        """
        SELECT EXISTS (
            SELECT 1
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = current_schema()
              AND c.relname = ?
              AND c.relkind = 'r'
        )
        """.trimIndent(),
        { cursor ->
            QueryResult.Value(cursor.next().value && cursor.getBoolean(0) == true)
        },
        1,
    ) {
        bindString(0, name)
    }.value

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
