package com.zula.features.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

const val OAUTH_STATE_COOKIE_NAME = "zula_oauth_state"
const val OAUTH_MODE_COOKIE_NAME = "zula_oauth_mode"

fun ApplicationCall.setOAuthStateCookies(state: String, mode: String) {
    val maxAge = 600
    response.cookies.append(
        Cookie(
            name = OAUTH_STATE_COOKIE_NAME,
            value = state,
            maxAge = maxAge,
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
    response.cookies.append(
        Cookie(
            name = OAUTH_MODE_COOKIE_NAME,
            value = mode,
            maxAge = maxAge,
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

fun ApplicationCall.readOAuthStateCookie(): String? = request.cookies[OAUTH_STATE_COOKIE_NAME]

fun ApplicationCall.readOAuthModeCookie(): String? = request.cookies[OAUTH_MODE_COOKIE_NAME]

fun ApplicationCall.clearOAuthStateCookies() {
    for (name in listOf(OAUTH_STATE_COOKIE_NAME, OAUTH_MODE_COOKIE_NAME)) {
        response.cookies.append(
            Cookie(
                name = name,
                value = "",
                maxAge = 0,
                path = "/",
                secure = true,
                httpOnly = true,
                extensions = mapOf("SameSite" to "Lax"),
            ),
        )
    }
}

suspend fun ApplicationCall.respondOAuthPopupResult(success: Boolean, error: String? = null) {
    val payload = buildString {
        append("{")
        append("\"type\":\"zula.oauth.complete\",")
        append("\"success\":")
        append(if (success) "true" else "false")
        if (!error.isNullOrBlank()) {
            append(",\"error\":")
            append(jsonString(error))
        }
        append("}")
    }
    respondText(
        """
        <!DOCTYPE html>
        <html lang="en">
          <head><meta charset="utf-8"><title>Signing in…</title></head>
          <body>
            <script>
              (function () {
                var payload = $payload;
                if (window.opener) {
                  window.opener.postMessage(payload, window.location.origin);
                  window.close();
                  return;
                }
                window.location.replace("/oauth/complete?success=${if (success) "1" else "0"}");
              })();
            </script>
          </body>
        </html>
        """.trimIndent(),
        ContentType.Text.Html,
    )
}

private fun jsonString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
