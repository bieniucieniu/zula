plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqlDelight)
}

group = "com.zula"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("io.ktor.utils.io.ExperimentalKtorApi")
    }
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}

sqldelight {
    databases {
        register("Database") {
            packageName.set("com.zula")
            deriveSchemaFromMigrations.set(true)
            // Export .sqm -> valid SQL for Flyway etc. Run manually:
            // ./gradlew :server:generateMainDatabaseMigrations
            migrationOutputDirectory = layout.buildDirectory.dir("resources/db/migrations")
            migrationOutputFileFormat = ".sql"
            dialect("app.cash.sqldelight:postgresql-dialect:${libs.versions.sqlDelight.get()}")
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateSqlDelightInterface")
}

dependencies {
    implementation(ktorLibs.client.apache)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.cachingHeaders)
    implementation(ktorLibs.server.compression)
    implementation(ktorLibs.server.conditionalHeaders)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.server.sse)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.swagger)
    implementation(ktorLibs.server.callLogging)
    implementation(libs.jobrunr)
    implementation(libs.jobrunr.kotlin)
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)
    implementation(libs.logback.classic)
    implementation(libs.ucasoft.ktorSimpleCache)
    implementation(libs.ucasoft.ktorSimpleMemoryCache)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)

    implementation(libs.sqlDelight.jdbcDrver)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.kubernetes.client)
    implementation(libs.aws.s3)
}
