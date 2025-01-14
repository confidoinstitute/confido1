package users

import kotlinx.serialization.Serializable

@Serializable
enum class UserType {
    ADMIN,
    MEMBER,
    GUEST,
    GHOST; // Represents a deleted user, preserving their predictions/history

    fun isProper() =
        when(this) {
            ADMIN -> true
            MEMBER -> true
            else -> false
        }

    fun isActive() = 
        when(this) {
            GHOST -> false
            else -> true
        }
}
