package tools.confido.payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.search.SearchScope

/** Workspace search request. An empty [scope] is treated as [SearchScope.default]. */
@Serializable
data class SearchRequest(
    val query: String,
    val scope: Set<SearchScope> = SearchScope.default,
)
