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

tasks.register<JavaExec>("generateJwtK8sSecret") {
    group = "security"
    description = "Generate RSA JWT keys and print a Kubernetes Secret manifest"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.zula.core.security.jwt.JwtKeyGeneratorKt")
    val namespace = (findProperty("jwtSecretNamespace") as String?) ?: "zula"
    val secretName = (findProperty("jwtSecretName") as String?) ?: "zula-jwt-keys"
    systemProperty("jwtKeysCommand", "generate")
    systemProperty("jwtSecretNamespace", namespace)
    systemProperty("jwtSecretName", secretName)
}

tasks.register<JavaExec>("manageJwtKeys") {
    group = "security"
    description = "Manage JWT keys: generate, validate, pull, push (Kubernetes)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.zula.core.security.jwt.JwtKeyGeneratorKt")
    val command = (findProperty("jwtKeysCommand") as String?) ?: "generate"
    val namespace = (findProperty("jwtSecretNamespace") as String?) ?: "zula"
    val secretName = (findProperty("jwtSecretName") as String?) ?: "zula-jwt-keys"
    val k8sEnabled = (findProperty("jwtK8sEnabled") as String?) ?: "false"
    systemProperty("jwtKeysCommand", command)
    systemProperty("jwtSecretNamespace", namespace)
    systemProperty("jwtSecretName", secretName)
    systemProperty("jwtK8sEnabled", k8sEnabled)
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
    implementation(libs.kubernetes.client)
}
