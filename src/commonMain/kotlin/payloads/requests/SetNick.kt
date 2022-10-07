package payloads.requests

import kotlinx.serialization.Serializable

@Serializable
data class SetNick(
    val name: String
)