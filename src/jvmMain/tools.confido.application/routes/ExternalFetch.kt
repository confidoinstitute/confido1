package tools.confido.application.routes

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Lazy-initialized Ktor HTTP client for fetching user-supplied external URLs
 * (Google Sheets / Slides exports). Shared so we keep one connection pool.
 */
internal val externalFetchClient: HttpClient by lazy {
    HttpClient(CIO) {
        followRedirects = true
        engine {
            // Google renders a deck to PDF on the fly; a multi-MB export routinely
            // takes 15–20 s, past CIO's 15 s default request timeout (observed as
            // "Request timeout has expired" on a deck that curl fetched fine).
            requestTimeout = 120_000
        }
    }
}
