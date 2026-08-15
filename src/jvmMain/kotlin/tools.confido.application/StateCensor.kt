package tools.confido.application

import kotlinx.datetime.Instant
import rooms.*
import tools.confido.application.sessions.TransientData
import tools.confido.extensions.ServerExtension
import tools.confido.question.Comment
import tools.confido.question.CommentVisibility
import tools.confido.question.Question
import tools.confido.question.RoomComment
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.store.commentData
import tools.confido.utils.*
import users.User
import users.UserPresentation
import users.UserType

/**
 * @author Ministry of Truth
 */
class StateCensor(val sess: UserSession) {
    val user = sess.user
    val state = serverState

    // Lazy: only the users-map censoring needs this; topic bodies that censor rooms
    // alone (the directory/detail topics) never pay for the walk.
    val referencedUsers: Set<Ref<User>> by lazy { computeReferencedUsers() }

    private fun computeReferencedUsers(): Set<Ref<User>> {
        val referencedUsers = mutableSetOf<Ref<User>>()
        // Iterates precomputed per-thread author sets (folds over the comment archives'
        // change streams), not individual comments; cost is O(threads × distinct authors).
        commentData.questionCommentAuthors.forEach { qref, authors ->
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
            referencedUsers.addAll(authors)
        }

        commentData.roomCommentAuthors.forEach { roomRef, authors ->
            val room = roomRef.deref() ?: return@forEach
            if (!room.hasPermission(user, RoomPermission.VIEW_ROOM_COMMENTS)) return@forEach
            referencedUsers.addAll(authors)
        }
        return referencedUsers
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

        // Populate presentation running flag from in-memory run state (visible to all members)
        val canManageQuestions = room.hasPermission(user, RoomPermission.MANAGE_QUESTIONS)
        val presentationRunning = if (serverState.presentationRunStates.containsKey(room.ref)) true else null

        var censored = room.copy(
            questions = room.questions.filter {
                (it.deref() ?: return@filter false).visible || room.hasPermission(user, RoomPermission.VIEW_HIDDEN_QUESTIONS)
            },
            // guests cannot see member list; members stays as direct members only
            members = room.members.mapNotNull { censorMembership(room, it) },
            // _effectiveMembers carries the full effective list (including implied) for the client
            _effectiveMembers = effMembers.mapNotNull { censorMembership(room, it) },
            _effectiveQuestions = effQuestions,
            _presentationRunning = presentationRunning,
            // Strip presentation ref for non-moderators
            presentation = if (canManageQuestions) room.presentation else null,
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

    /**
     * Directory-level room censoring (§4.2 "rooms" topic): the full censor minus
     * per-member data — other users' memberships, invite links, presentation state.
     * The viewer's *own* (effective) membership is kept so client-side permission
     * checks work everywhere, and question refs are kept for the client-side
     * question→room reverse lookup and cross-room surfaces (dashboard, search).
     * Full member lists travel on the per-room "/state/rooms/{id}" detail topic.
     */
    fun censorRoomDirectory(room: Room): Room? = censorRoom(room)?.let { c ->
        c.copy(
            members = c.members.filter { it.user eqid user },
            _effectiveMembers = c._effectiveMembers?.filter { it.user eqid user },
            inviteLinks = emptyList(),
            presentation = null,
            _presentationRunning = null,
        )
    }

    fun censorRoomsDirectory() = state.rooms.mapValuesNotNull { censorRoomDirectory(it.value) }

    fun <T> censorQuestionInfo(what: Map<Ref<Question>, T>) =
        what.filterKeys { k -> k.deref()?.let{ q -> censorQuestion(q) } != null }

    fun censorUsers() =
        state.users.mapValuesNotNull { censorUser(it.value) }

    /** The "/state/questions" topic payload: censored questions + per-question counts. */
    fun censorQuestionsState() = QuestionsState(
        questions = censorQuestions(),
        commentCount = censorQuestionInfo(state.commentCount),
        predictorCount = censorQuestionInfo(state.predictorCount),
        predictionCount = censorQuestionInfo(state.predictionCount),
    )
}

/** The per-user "/state/self" topic payload (PERSISTENCE_SYNC_REDESIGN.md §4.2). */
fun selfState(sess: UserSession, transientData: TransientData): SelfState {
    val user = sess.user
    return SelfState(
        // expiryTime is renewed on every authenticated request and read by no client
        // code; pinning it keeps the payload stable so dedup can do its job (otherwise
        // the acting user re-receives the payload on each of their requests).
        session = sess.copy(expiryTime = Instant.DISTANT_FUTURE),
        myPredictions = if (user != null)
            serverState.userPred.mapValuesNotNull { it.value[user.ref] } else emptyMap(),
        myPasswordIsSet = user?.password != null,
        presenterWindowActive = transientData.activePresenterWindows > 0,
    )
}

enum class UserPresentationContext {
    COMMENT_AUTHOR,
    SCOREBOARD,
    INDIVIDUAL_PREDICTION,
}

fun getUserPresentation(user: User?, observer: User, context: UserPresentationContext, room: Room?): UserPresentation {
    ServerExtension.forEach { ext-> ext.getUserPresentation(user, observer, context, room)?.let { return it } }
    if (user == null) return UserPresentation(false, "(Deleted user)")
    val amIModerator = room?.hasPermission(observer, RoomPermission.MANAGE_MEMBERS) ?: false
    return UserPresentation(user.id == observer.id, user.nick ?: (if (amIModerator) user.email else null) ?: "(Anonymous)",
    color = stringToColor(user.id))
}