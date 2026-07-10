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
            migrationOutputDirectory = layout.buildDirectory.dir("resources/db/migrations")
            migrationOutputFileFormat = ".sql"
            dialect("app.cash.sqldelight:postgresql-dialect:${libs.versions.sqlDelight.get()}")
        }
    }
}

dependencies {
    implementation(ktorLibs.client.apache)
    implementation(ktorLibs.client.core)
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
    implementation(libs.damirdenisTudor.ktorServerRabbitmq)
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
}
