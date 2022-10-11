package rooms

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class RoomRole(val permissions: Set<RoomPermission>) {
    @Transient
    abstract val id: String
    @Transient
    abstract val name: String

    fun hasPermission(permission: RoomPermission): Boolean {
        return permissions.contains(permission)
    }
}

@Serializable
object Forecaster : RoomRole(setOf(
        RoomPermission.VIEW_QUESTIONS,
        RoomPermission.SUBMIT_PREDICTION,
        RoomPermission.VIEW_QUESTION_COMMENTS,
        RoomPermission.VIEW_ROOM_COMMENTS,
        RoomPermission.POST_ROOM_COMMENT,
        RoomPermission.POST_QUESTION_COMMENT,
)) {
    override val id = "forecaster"
    override val name = "Forecaster"
}

@Serializable
object Moderator : RoomRole(
    Forecaster.permissions + setOf(
        RoomPermission.ADD_QUESTION,
        RoomPermission.VIEW_HIDDEN_QUESTIONS,
        RoomPermission.VIEW_ALL_GROUP_PREDICTIONS,
        RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS,
        RoomPermission.MANAGE_QUESTIONS,
        RoomPermission.MANAGE_MEMBERS,
    )
) {
    override val id = "moderator"
    override val name = "Moderator"
}