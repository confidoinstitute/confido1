package rooms

import kotlinx.serialization.Serializable

@Serializable
sealed class RoomRole(val permissions: Set<RoomPermission>) {
    fun hasPermission(permission: RoomPermission): Boolean {
        return permissions.contains(permission)
    }
}

@Serializable
object Forecaster : RoomRole(setOf(RoomPermission.VIEW_QUESTIONS, RoomPermission.SUBMIT_PREDICTION))

@Serializable
object Moderator : RoomRole(
    setOf(
        RoomPermission.VIEW_QUESTIONS,
        RoomPermission.SUBMIT_PREDICTION,
        RoomPermission.ADD_QUESTION,
        RoomPermission.SUGGEST_QUESTION,
        RoomPermission.VIEW_HIDDEN_QUESTIONS,
        RoomPermission.VIEW_ALL_PREDICTIONS,
        RoomPermission.MANAGE_QUESTIONS,
        RoomPermission.MANAGE_MEMBERS
    )
)

