package com.zula.core.security

import io.ktor.server.application.*
import io.ktor.server.request.*

/**
 * Public origin as seen by the client / reverse proxy.
 *
 * Prefer [configuredFallback] (`APP_URL`) when set — absolute links (OAuth callbacks)
 * must not use the backend bind host (e.g. `:8000` behind Vite `:3000`).
 */
fun ApplicationCall.publicBaseUrl(configuredFallback: String? = null): String {
    configuredFallback?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }

    val local = request.local
    val scheme = request.header("X-Forwarded-Proto")?.substringBefore(',')?.trim()
        ?: local.scheme

    val rawHost = request.header("X-Forwarded-Host")?.substringBefore(',')?.trim()
        ?: request.header("Host")?.substringBefore(',')?.trim()
        ?: local.serverHost

    if (rawHost.isNullOrBlank() || rawHost == "0.0.0.0") {
        error("Cannot infer public URL from request; set security.appUrl / APP_URL")
    }

    val (host, hostPort) = splitHostPort(rawHost)
    val port = request.header("X-Forwarded-Port")?.substringBefore(',')?.trim()?.toIntOrNull()
        ?: hostPort
        ?: local.serverPort

    val defaultPort = when (scheme) {
        "https" -> 443
        "http" -> 80
        else -> port
    }
    val portSuffix = if (port == defaultPort) "" else ":$port"
    return "$scheme://$host$portSuffix"
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

/** Split `host`, `host:port`, or `[ipv6]:port` into host + optional port. */
private fun splitHostPort(raw: String): Pair<String, Int?> {
    if (raw.startsWith('[')) {
        val end = raw.indexOf(']')
        if (end > 0) {
            val host = raw.substring(0, end + 1)
            val rest = raw.substring(end + 1)
            val port = rest.removePrefix(":").toIntOrNull()
            return host to port
        }
    }
    val colon = raw.lastIndexOf(':')
    if (colon > 0 && raw.indexOf(':') == colon) {
        val port = raw.substring(colon + 1).toIntOrNull()
        if (port != null) return raw.substring(0, colon) to port
    }
    return raw to null
}
