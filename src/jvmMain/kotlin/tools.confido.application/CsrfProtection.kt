package tools.confido.application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

private val SAFE_METHODS = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

private fun ApplicationRequest.isWebSocketUpgrade() =
    headers[HttpHeaders.Upgrade]?.equals("websocket", ignoreCase = true) == true

// Strip an explicit default port so that e.g. an Origin of "https://example.com"
// matches a Host header of "example.com:443" and vice versa.
private fun normalizeAuthority(authority: String) =
    authority.removeSuffix(":443").removeSuffix(":80")

private fun urlAuthority(url: String): String? = try {
    val parsed = Url(url)
    normalizeAuthority(
        if (parsed.port == parsed.protocol.defaultPort) parsed.host
        else "${parsed.host}:${parsed.port}"
    )
} catch (e: Throwable) {
    null
}

// A reverse proxy may rewrite the Host header; in that case the browser-visible
// authority is the one from CONFIDO_BASE_URL, so accept that one as well.
private val configuredAuthority = System.getenv("CONFIDO_BASE_URL")?.let { urlAuthority(it) }

/** Returns a log message describing the violation if this is a cross-site request, null if it is fine. */
private fun crossSiteViolation(call: ApplicationCall): String? {
    val request = call.request
    // Safe methods carry no state change; websocket upgrades are GET but stream out
    // privileged state, so they are checked despite being GET.
    if (request.httpMethod in SAFE_METHODS && !request.isWebSocketUpgrade()) return null

    // All modern browsers send Sec-Fetch-Site and a page cannot forge it.
    // "none" means user-initiated (e.g. address bar), which cannot carry a cross-site payload.
    request.headers["Sec-Fetch-Site"]?.let {
        return if (it == "same-origin" || it == "none") null else "Sec-Fetch-Site: $it"
    }

    // Older browsers: compare Origin (or Referer) against the Host header. Requests
    // with neither header come from non-browser clients, which have no ambient
    // cookie authentication to abuse.
    val origin = request.headers[HttpHeaders.Origin] ?: request.headers[HttpHeaders.Referrer] ?: return null
    val originAuthority = urlAuthority(origin) ?: return "unparseable Origin/Referer: $origin"
    val hostAuthority = request.headers[HttpHeaders.Host]?.let { normalizeAuthority(it) }
        ?: return "missing Host header"
    return if (originAuthority == hostAuthority || originAuthority == configuredAuthority) null
    else "Origin $origin does not match Host $hostAuthority"
}

/**
 * Rejects state-changing requests and websocket upgrades originating from another
 * site. Defense in depth against CSRF and cross-site websocket hijacking on top of
 * the SameSite=Lax session cookie.
 */
fun Application.installCsrfProtection() {
    intercept(ApplicationCallPipeline.Plugins) {
        val violation = crossSiteViolation(call) ?: return@intercept
        call.application.log.info("Rejecting cross-site request to ${call.request.path()} ($violation)")
        call.respond(HttpStatusCode.Forbidden, "Cross-site requests are not allowed.")
        finish()
    }
}
