package users

import kotlinx.serialization.Serializable

@Serializable
enum class UserType {
    ADMIN,
    MEMBER,
    GUEST,
    ANON_GUEST;

    fun isProper() =
        when(this) {
            ADMIN -> true
            MEMBER -> true
            else -> false
        }
}