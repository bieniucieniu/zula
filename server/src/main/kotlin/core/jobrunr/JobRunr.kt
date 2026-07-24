package com.zula.core.jobrunr

import com.zaxxer.hikari.HikariDataSource
import com.zula.core.security.AuthProviderNames
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jobrunr.configuration.JobRunr
import org.jobrunr.kotlin.di.KoinJobActivator
import org.jobrunr.scheduling.BackgroundJob
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory
import org.koin.ktor.ext.getKoin
import org.slf4j.LoggerFactory

object JobRunrSmokeJobs {
    private val log = LoggerFactory.getLogger(JobRunrSmokeJobs::class.java)

    fun ping() {
        log.info("JobRunr ping job executed")
    }
}

fun Application.configureJobRunr() {
    val dataSource: HikariDataSource? = getKoin().getOrNull()

    if (dataSource != null) {
        val dashboardEnabled =
            environment.config.propertyOrNull("jobrunr.dashboardEnabled")
                ?.getString()
                ?.toBooleanStrictOrNull()
                ?: false
        val dashboardPort =
            environment.config.propertyOrNull("jobrunr.dashboardPort")
                ?.getString()
                ?.toIntOrNull()
                ?: 8008

        JobRunr.configure()
            .useJobActivator(KoinJobActivator)
            .useStorageProvider(SqlStorageProviderFactory.using(dataSource))
            .useBackgroundJobServer()
            .useDashboardIf(dashboardEnabled, dashboardPort)
            .initialize()

        log.info(
            "JobRunr started (background server on, dashboard={})",
            if (dashboardEnabled) "port $dashboardPort" else "off",
        )
    } else {
        log.info("JobRunr disabled, no database DataSource")
    }
}

/** Mount after JWT Authentication is installed (see configureRouting). */
fun Route.configureJobRunrRouting() {
    val enabled = application.getKoin().getOrNull<HikariDataSource>() != null
    authenticate(AuthProviderNames.JWT) {
        get("/jobs/ping") {
            if (!enabled) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("error" to "JobRunr disabled"),
                )
                return@get
            }
            val jobId = BackgroundJob.enqueue { JobRunrSmokeJobs.ping() }
            call.respond(HttpStatusCode.Accepted, mapOf("jobId" to jobId.toString()))
        }
    }
}
