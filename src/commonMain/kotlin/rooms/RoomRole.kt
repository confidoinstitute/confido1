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
object Viewer : RoomRole(setOf(
        RoomPermission.VIEW_QUESTIONS,
        RoomPermission.VIEW_QUESTION_COMMENTS,
        RoomPermission.VIEW_ROOM_COMMENTS,
        RoomPermission.VIEW_ALL_GROUP_PREDICTIONS,
)) {
    override val id = "viewer"
    override val name = "Viewer"
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
        RoomPermission.CREATE_INVITE_LINK,
        RoomPermission.VIEW_ALL_INVITE_TOKENS,
    )
) {
    override val id = "moderator"
    override val name = "Moderator"
}

@Serializable
object Owner : RoomRole(
    RoomPermission.values().toSet()
) {
    override val id = "owner"
    override val name = "Owner"
}