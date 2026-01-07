package extensions.shared

import kotlinx.serialization.Serializable

@Serializable
data class ValueWithUser(
    val nickname: String,
    val value: Double,
    val isSpecial: Boolean = false
)
