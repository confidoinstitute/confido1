package users

import kotlinx.serialization.Serializable

@Serializable
enum class UserType {
    ADMIN,
    MEMBER,
    GUEST;

    fun isProper() =
        when(this) {
            ADMIN -> true
            MEMBER -> true
            else -> false
        }
}