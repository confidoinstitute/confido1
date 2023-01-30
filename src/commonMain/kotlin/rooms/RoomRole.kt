package rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.state.FeatureFlag
import tools.confido.state.appConfig

enum class RoomPermission {
    VIEW_QUESTIONS,
    VIEW_QUESTION_COMMENTS,
    VIEW_ROOM_COMMENTS,
    POST_QUESTION_COMMENT,
    POST_ROOM_COMMENT,
    SUBMIT_PREDICTION,
    ADD_QUESTION,
    SUGGEST_QUESTION,
    VIEW_HIDDEN_QUESTIONS,
    VIEW_ALL_INVITE_TOKENS,
    CREATE_INVITE_LINK,
    /** See all group predictions regardless of question setting. */
    VIEW_ALL_GROUP_PREDICTIONS,
    VIEW_ALL_RESOLUTIONS,
    VIEW_INDIVIDUAL_PREDICTIONS,
    MANAGE_QUESTIONS,
    MANAGE_MEMBERS,
    MANAGE_COMMENTS,
    ROOM_OWNER,
}

@Serializable
sealed class RoomRole(val permissions: Set<RoomPermission>) {
    abstract val id: String
    abstract val name: String

    fun hasPermission(permission: RoomPermission): Boolean {
        return permissions.contains(permission)
    }
}

fun canChangeRole(myRole: RoomRole?, otherRole: RoomRole): Boolean {
    if (myRole == null) return false
    val owner = (myRole is Owner)
    val moderator = (owner || myRole is Moderator)
    return when(otherRole) {
        is Viewer -> moderator
        is Forecaster -> moderator
        is QuestionWriter -> (FeatureFlag.QUESTION_WRITER_ROLE in appConfig.featureFlags &&  moderator)
        is Moderator -> owner
        is Owner -> owner
    }
}

val RoomRole.isAvailableToGuests: Boolean
    get() = when (this) {
        is Viewer -> true
        is Forecaster -> true
        is QuestionWriter -> true
        // Guest moderators and owners would not be able to see the users of the organization
        // in the moderation interface because of the StateCensor.
        is Moderator -> false
        is Owner -> false
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
@SerialName("question_writer")
object QuestionWriter : RoomRole(
    Forecaster.permissions +
    setOf(
    RoomPermission.ADD_QUESTION
)) {
    override val id = "question_writer"
    override val name = "Question Writer"
}
@Serializable
object Moderator : RoomRole(
    Forecaster.permissions + setOf(
        RoomPermission.ADD_QUESTION,
        RoomPermission.VIEW_HIDDEN_QUESTIONS,
        RoomPermission.VIEW_ALL_GROUP_PREDICTIONS,
        RoomPermission.VIEW_ALL_RESOLUTIONS,
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