package com.zula.features.auth

import com.zula.core.security.ACCESS_COOKIE_NAME
import com.zula.core.security.JwtConfig
import com.zula.core.security.REFRESH_COOKIE_NAME
import com.zula.features.auth.domain.AuthTokensResponse
import io.ktor.http.*
import io.ktor.server.application.*
import org.koin.ktor.ext.get


fun ApplicationCall.setAccessCookies(tokens: AuthTokensResponse) {
    setAccessCookie(tokens.accessToken, tokens.expiresIn)
    if (tokens.refreshToken != null) {
        val jwtConfig: JwtConfig = get()
        setRefreshCookie(tokens.refreshToken, jwtConfig.refreshTokenTtlSeconds)
    }
}

fun ApplicationCall.setAccessCookie(token: String, maxAgeSeconds: Long) {
    response.cookies.append(
        Cookie(
            name = ACCESS_COOKIE_NAME,
            value = token,
            maxAge = maxAgeSeconds.toInt(),
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

fun ApplicationCall.setRefreshCookie(token: String, maxAgeSeconds: Long) {
    response.cookies.append(
        Cookie(
            name = REFRESH_COOKIE_NAME,
            value = token,
            maxAge = maxAgeSeconds.toInt(),
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        ),
    )
}

fun ApplicationCall.clearAuthCookies() {
    response.cookies.append(
        Cookie(
            name = ACCESS_COOKIE_NAME,
            value = "",
            maxAge = 0,
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
    response.cookies.append(
        Cookie(
            name = REFRESH_COOKIE_NAME,
            value = "",
            maxAge = 0,
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        ),
    )
}

fun ApplicationCall.readRefreshCookie(): String? = request.cookies[REFRESH_COOKIE_NAME]

fun ApplicationCall.readAccessCookie(): String? = request.cookies[ACCESS_COOKIE_NAME]
