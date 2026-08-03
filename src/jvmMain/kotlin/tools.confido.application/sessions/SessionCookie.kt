package tools.confido.application.sessions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.date.*
import kotlin.math.max

const val COOKIE_NAME = "session"
/** Non-HttpOnly cookie telling the frontend whether a user is logged in. Carries no
 *  secret; the actual session id lives in the HttpOnly [COOKIE_NAME] cookie. */
const val LOGIN_HINT_COOKIE_NAME = "logged_in"
const val SESSION_MAX_AGE: Long = 365L * 24 * 3600 // 365 days

// The Secure attribute would make the cookie disappear on plain-HTTP deployments,
// so it must be derived from the configured public URL (same one the mailer uses).
private val cookieSecure = (System.getenv("CONFIDO_BASE_URL") ?: "").startsWith("https://")

fun readCookie(call: ApplicationCall): String? {
    return call.request.cookies[COOKIE_NAME]
}

private fun makeCookie(name: String, value: String, persistent: Boolean, httpOnly: Boolean) = Cookie(
    name,
    value = value,
    maxAge = if (persistent) SESSION_MAX_AGE.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else null,
    expires = if (persistent) GMTDate() + SESSION_MAX_AGE * 1000L else null,
    path = "/",
    secure = cookieSecure,
    httpOnly = httpOnly,
    extensions = mapOf("SameSite" to "Lax")
)

fun sendCookie(call: ApplicationCall, value: String, persistent: Boolean) {
    call.response.cookies.append(makeCookie(COOKIE_NAME, value, persistent, httpOnly = true))
}

fun sendLoginHintCookie(call: ApplicationCall, persistent: Boolean) {
    call.response.cookies.append(makeCookie(LOGIN_HINT_COOKIE_NAME, "1", persistent, httpOnly = false))
}

fun hasLoginHintCookie(call: ApplicationCall) = call.request.cookies[LOGIN_HINT_COOKIE_NAME] != null

private fun makeClearingCookie(name: String) = Cookie(
    name,
    value = "",
    maxAge = 0,
    expires = GMTDate.START,
    // Must match the path the cookie was set with, or the browser keeps the old one.
    path = "/"
)

fun clearLoginHintCookie(call: ApplicationCall) {
    call.response.cookies.append(makeClearingCookie(LOGIN_HINT_COOKIE_NAME))
}

fun clearCookie(call: ApplicationCall) {
    call.response.cookies.append(makeClearingCookie(COOKIE_NAME))
    clearLoginHintCookie(call)
}
