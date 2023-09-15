package tools.confido.application.sessions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.date.*
import kotlin.math.max

const val COOKIE_NAME = "session"
const val SESSION_MAX_AGE: Long = 365L * 24 * 3600 // 365 days

fun readCookie(call: ApplicationCall): String? {
    return call.request.cookies[COOKIE_NAME]
}

fun sendCookie(call: ApplicationCall, value: String, persistent: Boolean) {
    val cookie = Cookie(
        COOKIE_NAME,
        value = value,
        maxAge = if (persistent) SESSION_MAX_AGE.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0,
        expires = if (persistent) GMTDate() + SESSION_MAX_AGE * 1000L else null,
        path = "/",
        extensions = mapOf("SameSite" to "Lax")
    )
    call.response.cookies.append(cookie)
}

fun clearCookie(call: ApplicationCall) {
    val cookie = Cookie(
        COOKIE_NAME,
        value = "",
        maxAge = 0,
        expires = GMTDate.START
    )
    call.response.cookies.append(cookie)
}