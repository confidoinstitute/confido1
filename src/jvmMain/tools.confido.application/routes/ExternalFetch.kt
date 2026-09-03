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
    }
}
