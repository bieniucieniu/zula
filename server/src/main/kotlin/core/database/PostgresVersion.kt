package com.zula.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

internal const val MIN_POSTGRES_VERSION_NUM = 180_000

internal fun isPostgresVersionSupported(versionNum: Int): Boolean =
    versionNum >= MIN_POSTGRES_VERSION_NUM

internal fun postgresMajorVersion(versionNum: Int): Int =
    versionNum / 10_000

internal fun readPostgresVersionNum(driver: SqlDriver): Int =
    driver.executeQuery(
        identifier = null,
        sql = "SHOW server_version_num",
        mapper = { cursor ->
            QueryResult.Value(
                if (cursor.next().value) {
                    cursor.getString(0)?.toIntOrNull()
                        ?: error("PostgreSQL server_version_num was not numeric")
                } else {
                    error("PostgreSQL server_version_num was empty")
                },
            )
        },
        parameters = 0,
    ).value

internal fun requirePostgres18OrNewer(driver: SqlDriver) {
    val versionNum = readPostgresVersionNum(driver)
    if (!isPostgresVersionSupported(versionNum)) {
        val major = postgresMajorVersion(versionNum)
        error(
            "PostgreSQL 18+ required for native uuidv7(); connected server reports version $major " +
                "(server_version_num=$versionNum)",
        )
    }
}
