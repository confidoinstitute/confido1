package tools.confido.application

import rooms.InviteLink
import rooms.Room
import rooms.RoomMembership
import rooms.RoomPermission
import tools.confido.application.sessions.TransientData
import tools.confido.question.Comment
import tools.confido.question.Question
import tools.confido.question.RoomComment
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.utils.*
import users.User
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
        if (!room.hasPermission(user, RoomPermission.VIEW_QUESTIONS)) return null
        return room.copy(
            questions = room.questions.filter {
                (it.deref() ?: return@filter false).visible || room.hasPermission(user, RoomPermission.VIEW_HIDDEN_QUESTIONS)
            },
            // guests cannot see member list
            members = room.members.mapNotNull { censorMembership(room, it) },
            inviteLinks = if (user?.type in setOf(UserType.ADMIN, UserType.MEMBER)) room.inviteLinks.mapNotNull { censorInviteLink(room, it) } else emptyList(),
        )
    }


    fun censorQuestion(q: Question): Question? {
        val room = state.questionRoom[q.ref]?.deref() ?: return null
        if (!room.hasPermission(user, RoomPermission.VIEW_QUESTIONS)) return null
        if (!q.visible && !room.hasPermission(user, RoomPermission.VIEW_HIDDEN_QUESTIONS)) return null

        var ret = q
        // Hide prepared resolutions if question is not set to resolved yet.
        if (!ret.resolved && !room.hasPermission(user, RoomPermission.VIEW_ALL_RESOLUTIONS)) {
            ret = ret.copy(resolution = null)
        }
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