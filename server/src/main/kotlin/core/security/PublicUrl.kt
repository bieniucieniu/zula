package com.zula.core.security

import io.ktor.server.application.*
import io.ktor.server.request.*

/**
 * Public origin of the request as seen by the client / reverse proxy.
 *
 * Prefer [configuredFallback] (APP_URL) when set — OAuth redirect URIs and other
 * absolute links must not depend on the backend's bind host (e.g. :8000 behind Vite :3000).
 *
 * Otherwise infer from X-Forwarded-* / Host.
 */
fun ApplicationCall.publicBaseUrl(configuredFallback: String? = null): String {
    configuredFallback?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }

    val local = request.local
    val scheme = request.header("X-Forwarded-Proto")?.substringBefore(',')?.trim()
        ?: local.scheme

    val forwardedHost = request.header("X-Forwarded-Host")?.substringBefore(',')?.trim()
    val rawHost = forwardedHost
        ?: request.header("Host")?.substringBefore(',')?.trim()
        ?: local.serverHost

    if (rawHost.isNullOrBlank() || rawHost == "0.0.0.0") {
        error("Cannot infer public URL from request; set security.appUrl / APP_URL")
    }

    val hostHasPort = rawHost.contains(':') && !rawHost.startsWith('[')
    val host = if (hostHasPort) rawHost.substringBefore(':') else rawHost.trimEnd(']')
        .substringAfterLast('[')
        .ifBlank { rawHost }

    val port = request.header("X-Forwarded-Port")?.substringBefore(',')?.trim()?.toIntOrNull()
        ?: if (hostHasPort) {
            rawHost.substringAfterLast(':').substringBefore(']').toIntOrNull()
        } else {
            null
        }
        ?: local.serverPort

    val defaultPort = when (scheme) {
        "https" -> 443
        "http" -> 80
        else -> port
    }
    val portSuffix = if (port == defaultPort) "" else ":$port"
    val hostOut = if (hostHasPort) host else rawHost
    return "$scheme://$hostOut$portSuffix"
}

fun ApplicationCall.oauthCallbackUrl(path: String, configuredAppUrl: String? = null): String {
    val base = publicBaseUrl(configuredAppUrl).trimEnd('/')
    val rootPath = application.environment.config.propertyOrNull("ktor.deployment.rootPath")?.getString()
        ?.trim('/')
        .orEmpty()
    val callbackPath = path.trim('/')
    return if (rootPath.isEmpty()) {
        "$base/$callbackPath"
    } else {
        "$base/$rootPath/$callbackPath"
    }
}

fun normalizeIssuer(value: String): String = value.trimEnd('/')
