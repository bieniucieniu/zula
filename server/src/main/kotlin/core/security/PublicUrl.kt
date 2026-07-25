package com.zula.core.security

import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get

fun ApplicationCall.publicBaseUrl(configuredFallback: String? = null): String {
    val local = request.local
    val scheme = inferSchemeProto() ?: local.scheme
    val host = inferHost() ?: local.serverHost

    if (host.isBlank() || host == "0.0.0.0") {
        return configuredFallback?.trimEnd('/')
            ?: error("Cannot infer public URL from request; set security.appUrl / APP_URL")
    }

    val port = inferPort() ?: local.serverPort
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


//Cf-Visitor: {"scheme":"https"}
data class CloudflareVisitor(
    val scheme: String?
)

const val CLOUDFLARE_VISITOR_HEADER = "Cf-Visitor"

fun ApplicationCall.inferSchemeProto(json: Json = get()): String? {
    request.header(CLOUDFLARE_VISITOR_HEADER)?.let {
        runCatching {
            val cf: CloudflareVisitor = json.decodeFromString(it)
            if (cf.scheme != null) return cf.scheme
        }
    }
    val url = request.header("X-Forwarded-Proto")?.substringBefore(',')?.trim()

    return url
}

fun ApplicationCall.inferHost(): String? {
    val header = request.header("X-Forwarded-Host") ?: request.header("Host")
    return header?.substringBefore(',')?.substringBefore(":")?.trim()
}

fun ApplicationCall.inferPort(): Int? =
    request.header("X-Forwarded-Port")?.substringBefore(',')?.trim()?.toIntOrNull()

fun normalizeIssuer(value: String): String = value.trimEnd('/')
