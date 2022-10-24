package tools.confido.application

import rooms.InviteLink
import rooms.Room
import rooms.RoomMembership
import rooms.RoomPermission
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
class StateCensor(val sess: UserSession) {
    val user = sess.user
    val state = serverState
    val referencedUsers: MutableSet<Ref<User>> = mutableSetOf()

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
        var ret = q
        if (!q.visible && !room.hasPermission(user, RoomPermission.VIEW_HIDDEN_QUESTIONS)) return null
        return ret
    }

    fun censorQuestionComments() =
        state.questionComments.mapValuesNotNull { (qref, comments)->
                val q = qref.deref() ?: return@mapValuesNotNull null
                val room = state.questionRoom[q.ref]?.deref() ?: return@mapValuesNotNull null
                if (!room.hasPermission(user, RoomPermission.VIEW_QUESTION_COMMENTS)) return@mapValuesNotNull null
                referencedUsers.addAll(comments.map { it.value.user })
                comments
            }

    fun censorRoomComments(): Map<Ref<Room>, Map<String, RoomComment>> =
        state.roomComments.mapValuesNotNull { (roomRef, comments)->
                val room = roomRef.deref() ?: return@mapValuesNotNull null
                if (!room.hasPermission(user, RoomPermission.VIEW_ROOM_COMMENTS)) return@mapValuesNotNull null
                referencedUsers.addAll(comments.map { it.value.user })
                comments
            }

    fun censorGroupPred() =
        state.groupPred.filterKeys { qref->
            val q = qref.deref() ?: return@filterKeys false
            val room = state.questionRoom[q.ref]?.deref() ?: return@filterKeys false
            (
                    (room.hasPermission(user, RoomPermission.VIEW_QUESTIONS) && q.groupPredVisible)
                            || room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
            )
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

    fun censor(): SentState {
        return SentState(
            rooms = censorRooms(),
            questions = censorQuestions(),
            predictorCount = censorQuestionInfo(state.predictorCount),
            predictionCount = censorQuestionInfo(state.predictionCount),
            roomComments = censorRoomComments(),
            questionComments = censorQuestionComments(),
            groupPred = censorGroupPred(),
            users = censorUsers(), // MUST be AFTER roomComments and questionComments in order to fill referencedUsers
            myPredictions = getMyPredictions(),
            session = sess,
            devMode = devMode,
        )
    }
}