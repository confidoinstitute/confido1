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
object Viewer : RoomRole(
    setOf(RoomPermission.VIEW_QUESTIONS)
) {
    override val id = "viewer"
    override val name = "Viewer"
}

@Serializable
object Forecaster : RoomRole(setOf(RoomPermission.VIEW_QUESTIONS, RoomPermission.SUBMIT_PREDICTION)) {
    override val id = "forecaster"
    override val name = "Forecaster"
}

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
) {
    override val id = "moderator"
    override val name = "Moderator"
}