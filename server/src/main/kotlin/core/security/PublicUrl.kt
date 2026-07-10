package com.zula.core.security

import io.ktor.server.application.*
import io.ktor.server.request.*

fun ApplicationCall.publicBaseUrl(configuredFallback: String? = null): String {
    val local = request.local
    val scheme = request.header("X-Forwarded-Proto")?.substringBefore(',')?.trim()
        ?: local.scheme
    val host = request.header("X-Forwarded-Host")?.substringBefore(',')?.trim()
        ?: request.header("Host")?.substringBefore(':')?.trim()
        ?: local.serverHost

    if (host.isNullOrBlank() || host == "0.0.0.0") {
        return configuredFallback?.trimEnd('/')
            ?: error("Cannot infer public URL from request; set security.appUrl / APP_URL")
    }

    val port = request.header("X-Forwarded-Port")?.substringBefore(',')?.trim()?.toIntOrNull()
        ?: local.serverPort
    val defaultPort = when (scheme) {
        "https" -> 443
        "http" -> 80
        else -> port
    }
    val portSuffix = if (port == defaultPort) "" else ":$port"
    return "$scheme://$host$portSuffix"
}

fun ApplicationCall.oauthCallbackUrl(path: String, configuredFallback: String? = null): String {
    val base = publicBaseUrl(configuredFallback).trimEnd('/')
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
