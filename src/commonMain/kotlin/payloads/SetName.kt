package tools.confido.payloads

import kotlinx.serialization.Serializable

@Serializable
data class SetName(
    val name: String
)