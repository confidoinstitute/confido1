package tools.confido.application

import rooms.*
import tools.confido.application.sessions.TransientData
import tools.confido.extensions.ServerExtension
import tools.confido.question.Comment
import tools.confido.question.CommentVisibility
import tools.confido.question.Question
import tools.confido.question.RoomComment
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.utils.*
import users.User
import users.UserPresentation
import users.UserType

/**
 * @author Ministry of Truth
 */
class StateCensor(val sess: UserSession, val transientData: TransientData) {
    val user = sess.user
    val state = serverState
    val referencedUsers: MutableSet<Ref<User>> = mutableSetOf()

    init {
        state.questionComments.forEach { (qref, comments)->
            val q = qref.deref() ?: return@forEach
            val room = state.questionRoom[q.ref]?.deref() ?: return@forEach
            if (!room.hasPermission(user, RoomPermission.VIEW_QUESTION_COMMENTS)) return@forEach

            val canViewAll = room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
            val lastPrediction = state.userPred[q.ref]?.get(user?.ref)
            val canView = when (q.commentVisibility) {
                CommentVisibility.EVERYONE -> true
                CommentVisibility.ANSWERED -> lastPrediction != null
                CommentVisibility.MODERATOR_ONLY -> false
            }
            if (!(canViewAll || canView)) return@forEach
            referencedUsers.addAll(comments.map { it.value.user })
        }

        state.roomComments.forEach { (roomRef, comments)->
            val room = roomRef.deref() ?: return@forEach
            if (!room.hasPermission(user, RoomPermission.VIEW_ROOM_COMMENTS)) return@forEach
            referencedUsers.addAll(comments.map { it.value.user })
        }
    }

    fun censorMembership(room: Room, membership: RoomMembership) =
        membership.takeIf{user?.type in setOf(UserType.ADMIN, UserType.MEMBER) ||
                            membership.user eqid user}


    fun censorInviteLink(room: Room, inviteLink: InviteLink) =
        if (room.hasPermission(user, RoomPermission.VIEW_ALL_INVITE_TOKENS) || inviteLink.createdBy == user?.ref)
            inviteLink
        else
            inviteLink.copy(token = "")

    fun censorRoom(room: Room): Room? {
        // Room is visible if the user is an effective member (with any permission).
        // Which tabs/content they see is controlled by their effective permissions.
        if (user == null || room.effectiveMembers.none { it.user eqid user }) return null

        // Compute effective members with their effective permissions for wire transmission.
        val effMembers = room.effectiveMembers.map { m ->
            m.copy(effectivePermissions = room.memberEffectivePermissions(m))
        }

        // Compute effective questions if an extension provides them.
        val effQuestions = room.effectiveQuestions

        var censored = room.copy(
            questions = room.questions.filter {
                (it.deref() ?: return@filter false).visible || room.hasPermission(user, RoomPermission.VIEW_HIDDEN_QUESTIONS)
            },
            // guests cannot see member list; members stays as direct members only
            members = room.members.mapNotNull { censorMembership(room, it) },
            // _effectiveMembers carries the full effective list (including implied) for the client
            _effectiveMembers = effMembers.mapNotNull { censorMembership(room, it) },
            _effectiveQuestions = effQuestions,
            inviteLinks = if (user?.type in setOf(UserType.ADMIN, UserType.MEMBER)) room.inviteLinks.mapNotNull { censorInviteLink(room, it) } else emptyList(),
        )
        ServerExtension.forEach { censored = it.censorRoom(censored, user) }
        return censored
    }


    fun censorQuestion(q: Question): Question? {
        val room = state.questionRoom[q.ref]?.deref() ?: return null
        if (!room.hasPermission(user, RoomPermission.VIEW_QUESTIONS)) return null
        if (!q.visible && !room.hasPermission(user, RoomPermission.VIEW_HIDDEN_QUESTIONS)) return null

        var ret = q
        // Hide prepared resolutions if question is not set to resolved yet.
        if (!ret.resolutionVisible && !room.hasPermission(user, RoomPermission.VIEW_ALL_RESOLUTIONS)) {
            ret = ret.copy(resolution = null)
        }
        ServerExtension.forEach { ret = it.censorQuestion(ret, room, user) }
        return ret
    }

    fun censorUser(u: User): User? {
        if (user?.type in setOf(UserType.ADMIN, UserType.MEMBER) || user eqid u) return u.copy(password = null)
        else if (u.ref in referencedUsers) return u.copy(password = null, email = null)
        else return null
    }

    fun censorQuestions() = state.questions.mapValuesNotNull{ censorQuestion(it.value) }
    fun censorRooms() = state.rooms.mapValuesNotNull{ censorRoom(it.value) }

    fun <T> censorQuestionInfo(what: Map<Ref<Question>, T>) =
        what.filterKeys { k -> k.deref()?.let{ q -> censorQuestion(q) } != null }

    fun censorUsers() =
        state.users.mapValuesNotNull { censorUser(it.value) }

    fun getMyPredictions() =
        if (user!=null) state.userPred.mapValuesNotNull { it.value[user.ref] } else emptyMap()

    fun getCommentsILike() =
        user?.ref?.let { serverState.commentLikeManager.byUser[it] } ?: setOf()

    fun getMyPasswordIsSet() = user?.let { it.password != null } ?: false

    fun censor(): SentState {
        return SentState(
            rooms = censorRooms(),
            questions = censorQuestions(),
            commentCount = censorQuestionInfo(state.commentCount),
            predictorCount = censorQuestionInfo(state.predictorCount),
            predictionCount = censorQuestionInfo(state.predictionCount),
            users = censorUsers(),
            myPasswordIsSet = getMyPasswordIsSet(),
            myPredictions = getMyPredictions(),
            session = sess,
            presenterWindowActive = transientData.activePresenterWindows > 0,
        )
    }
}

enum class UserPresentationContext {
    COMMENT_AUTHOR,
    SCOREBOARD,
}

fun getUserPresentation(user: User?, observer: User, context: UserPresentationContext, room: Room?): UserPresentation {
    ServerExtension.forEach { ext-> ext.getUserPresentation(user, observer, context, room)?.let { return it } }
    if (user == null) return UserPresentation(false, "(Deleted user)")
    return UserPresentation(user.id == observer.id, user.nick ?: "(Anonymous)",
    color = stringToColor(user.id))
}