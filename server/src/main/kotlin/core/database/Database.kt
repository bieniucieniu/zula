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
        chat_messagesAdapter = Chat_messages.Adapter(
            idAdapter = KotlinUuidAdapter,
            room_idAdapter = KotlinUuidAdapter,
            sender_idAdapter = KotlinUuidAdapter,
        ),
        chat_participantsAdapter = Chat_participants.Adapter(
            room_idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
        ),
        chat_roomsAdapter = Chat_rooms.Adapter(
            idAdapter = KotlinUuidAdapter,
            trade_idAdapter = KotlinUuidAdapter,
            group_idAdapter = KotlinUuidAdapter,
        ),
        document_revisionsAdapter = Document_revisions.Adapter(
            idAdapter = KotlinUuidAdapter,
            document_idAdapter = KotlinUuidAdapter,
        ),
        documentsAdapter = Documents.Adapter(
            idAdapter = KotlinUuidAdapter,
            owner_user_idAdapter = KotlinUuidAdapter,
        ),
        feed_item_bookmarksAdapter = Feed_item_bookmarks.Adapter(
            user_idAdapter = KotlinUuidAdapter,
            feed_item_idAdapter = KotlinUuidAdapter,
        ),
        feed_item_commentsAdapter = Feed_item_comments.Adapter(
            idAdapter = KotlinUuidAdapter,
            feed_item_idAdapter = KotlinUuidAdapter,
            author_idAdapter = KotlinUuidAdapter,
        ),
        feed_item_likesAdapter = Feed_item_likes.Adapter(
            feed_item_idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
        ),
        feed_item_mediaAdapter = Feed_item_media.Adapter(
            idAdapter = KotlinUuidAdapter,
            feed_item_idAdapter = KotlinUuidAdapter,
        ),
        feed_item_traitsAdapter = Feed_item_traits.Adapter(
            feed_item_idAdapter = KotlinUuidAdapter,
            trait_idAdapter = KotlinUuidAdapter,
        ),
        feed_itemsAdapter = Feed_items.Adapter(
            idAdapter = KotlinUuidAdapter,
            author_idAdapter = KotlinUuidAdapter,
            group_idAdapter = KotlinUuidAdapter,
        ),
        group_membersAdapter = Group_members.Adapter(
            group_idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
        ),
        groupsAdapter = Groups.Adapter(
            idAdapter = KotlinUuidAdapter,
            created_byAdapter = KotlinUuidAdapter,
        ),
        media_objectsAdapter = Media_objects.Adapter(
            owner_user_idAdapter = KotlinUuidAdapter,
        ),
        seller_activity_statsAdapter = Seller_activity_stats.Adapter(
            user_idAdapter = KotlinUuidAdapter,
        ),
        sse_event_logAdapter = Sse_event_log.Adapter(
            idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
        ),
        trade_itemsAdapter = Trade_items.Adapter(
            idAdapter = KotlinUuidAdapter,
            trade_idAdapter = KotlinUuidAdapter,
            feed_item_idAdapter = KotlinUuidAdapter,
        ),
        trade_locationsAdapter = Trade_locations.Adapter(
            trade_idAdapter = KotlinUuidAdapter,
        ),
        trade_participantsAdapter = Trade_participants.Adapter(
            trade_idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
        ),
        tradesAdapter = Trades.Adapter(
            idAdapter = KotlinUuidAdapter,
            initiator_idAdapter = KotlinUuidAdapter,
            counterparty_idAdapter = KotlinUuidAdapter,
            feed_item_idAdapter = KotlinUuidAdapter,
        ),
        traitsAdapter = Traits.Adapter(
            idAdapter = KotlinUuidAdapter,
            parent_idAdapter = KotlinUuidAdapter,
        ),
        user_blocksAdapter = User_blocks.Adapter(
            blocker_idAdapter = KotlinUuidAdapter,
            blocked_idAdapter = KotlinUuidAdapter,
        ),
        user_identitiesAdapter = User_identities.Adapter(
            idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
        ),
        user_interest_profilesAdapter = User_interest_profiles.Adapter(
            user_idAdapter = KotlinUuidAdapter,
        ),
        user_portfolio_itemsAdapter = User_portfolio_items.Adapter(
            idAdapter = KotlinUuidAdapter,
            user_idAdapter = KotlinUuidAdapter,
            body_document_idAdapter = KotlinUuidAdapter,
            feed_item_idAdapter = KotlinUuidAdapter,
            trade_idAdapter = KotlinUuidAdapter,
        ),
        user_profile_bioAdapter = User_profile_bio.Adapter(
            user_idAdapter = KotlinUuidAdapter,
            document_idAdapter = KotlinUuidAdapter,
        ),
        user_profile_pinsAdapter = User_profile_pins.Adapter(
            user_idAdapter = KotlinUuidAdapter,
            portfolio_item_idAdapter = KotlinUuidAdapter,
        ),
        user_profilesAdapter = User_profiles.Adapter(
            user_idAdapter = KotlinUuidAdapter
        ),
        user_ratingsAdapter = User_ratings.Adapter(
            idAdapter = KotlinUuidAdapter,
            reviewer_idAdapter = KotlinUuidAdapter,
            reviewee_idAdapter = KotlinUuidAdapter,
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
        user_trait_followsAdapter = User_trait_follows.Adapter(
            user_idAdapter = KotlinUuidAdapter,
            trait_idAdapter = KotlinUuidAdapter,
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
