package com.zula.core.database

import com.zaxxer.hikari.HikariConfig
import com.zula.lib.utils.stringOrNull
import io.ktor.server.config.*

data class DatabaseConfig(
    val jdbcUrl: String = "",
    val username: String? = null,
    val password: String? = null,
    val maximumPoolSize: Int = 10,
    val autoMigrate: Boolean = true,
) {
    val isEnabled: Boolean
        get() = jdbcUrl.isNotBlank()


    fun toHikariConfig(): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = this@DatabaseConfig.jdbcUrl
            username = this@DatabaseConfig.username
            password = this@DatabaseConfig.password
            maximumPoolSize = this@DatabaseConfig.maximumPoolSize
            driverClassName = "org.postgresql.Driver"
        }

    companion object {
        fun from(config: ApplicationConfig?) = DatabaseConfig(
            jdbcUrl = config?.stringOrNull("jdbcUrl").orEmpty(),
            username = config?.stringOrNull("username"),
            password = config?.stringOrNull("password"),
            maximumPoolSize = config?.propertyOrNull("maximumPoolSize")?.getString()?.toIntOrNull() ?: 10,
            autoMigrate = config?.propertyOrNull("autoMigrate")?.getString()?.toBooleanStrictOrNull() ?: true,
        )
    }
}

class DatabaseConfigBuilder {
    lateinit var config: DatabaseConfig

    fun build(): DatabaseConfig = config
}