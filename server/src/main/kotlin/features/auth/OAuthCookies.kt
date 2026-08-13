package com.zula.features.auth

import io.ktor.http.*
import io.ktor.server.application.*

const val OAUTH_STATE_COOKIE_NAME = "zula_oauth_state"
const val OAUTH_NONCE_COOKIE_NAME = "zula_oauth_nonce"

fun ApplicationCall.setOAuthStateCookies(state: String, nonce: String) {
    val maxAge = 600
    val secure = cookieSecure()
    for ((name, value) in listOf(
        OAUTH_STATE_COOKIE_NAME to state,
        OAUTH_NONCE_COOKIE_NAME to nonce,
    )) {
        response.cookies.append(
            Cookie(
                name = name,
                value = value,
                maxAge = maxAge,
                path = "/",
                secure = secure,
                httpOnly = true,
                extensions = mapOf("SameSite" to "Lax"),
            ),
        )
    }
}

fun ApplicationCall.readOAuthStateCookie(): String? = request.cookies[OAUTH_STATE_COOKIE_NAME]

fun oauthStateMatches(expectedState: String?, cookieState: String?): Boolean =
    cookieState != null && expectedState != null && expectedState == cookieState

fun ApplicationCall.isOAuthStateMatch(
    expectedState: String? = request.queryParameters["state"],
    cookieState: String? = readOAuthStateCookie()
): Boolean = oauthStateMatches(expectedState, cookieState)

fun ApplicationCall.readOAuthNonceCookie(): String? = request.cookies[OAUTH_NONCE_COOKIE_NAME]

fun ApplicationCall.clearOAuthStateCookies() {
    val secure = cookieSecure()
    for (name in listOf(OAUTH_STATE_COOKIE_NAME, OAUTH_NONCE_COOKIE_NAME)) {
        response.cookies.append(
            Cookie(
                name = name,
                value = "",
                maxAge = 0,
                path = "/",
                secure = secure,
                httpOnly = true,
                extensions = mapOf("SameSite" to "Lax"),
            ),
        )
    }
}
