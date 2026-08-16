package tools.confido.application

import java.security.MessageDigest
import payloads.responses.MembershipPayload
import rooms.*
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.user
import tools.confido.extensions.ServerExtension
import tools.confido.question.Question
import tools.confido.refs.*
import tools.confido.state.*
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

    fun censorInviteLink(room: Room, inviteLink: InviteLink) =
        if (room.hasPermission(user, RoomPermission.VIEW_ALL_INVITE_TOKENS) || inviteLink.createdBy == user?.ref)
            inviteLink
        else
            inviteLink.copy(token = "")

    fun censorRoom(room: Room): Room? {
        // Room is visible if the user is an effective member (with any permission).
        // Which tabs/content they see is controlled by their effective permissions.
        if (user == null || room.effectiveMembers.none { it.user eqid user }) return null

        // Only the viewer's own (effective) membership travels on Room — it is what
        // client-side permission checks read. Other members' data is granted per
        // context by the room/<id>/members topic (censorRoomMembers) as presentations.
        val ownMembers = room.effectiveMembers.filter { it.user eqid user }.map { m ->
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
            members = room.members.filter { it.user eqid user },
            _effectiveMembers = ownMembers,
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

    // The users directory is only served to proper users (the topic gate); guests'
    // identity needs are covered by presentations embedded in payloads (§5.3).
    fun censorUser(u: User): User? {
        if (user?.type in setOf(UserType.ADMIN, UserType.MEMBER) || user eqid u) return u.copy(password = null)
        else return null
    }

    fun censorQuestions() = state.questions.mapValuesNotNull{ censorQuestion(it.value) }

    /**
     * Directory-level room censoring (§4.2 "rooms" topic): the room detail minus
     * invite links and presentation state. Memberships need no extra stripping —
     * [censorRoom] itself keeps only the viewer's own (so client-side permission
     * checks work everywhere); full member lists are a separate topic
     * ([censorRoomMembers]). Question refs are kept for the client-side
     * question→room reverse lookup and cross-room surfaces (dashboard, search).
     */
    fun censorRoomDirectory(room: Room): Room? = censorRoom(room)?.copy(
        inviteLinks = emptyList(),
        presentation = null,
        _presentationRunning = null,
    )

    /**
     * The "room/<id>/members" topic payload (§5.3): memberships as viewer-censored
     * presentations. Ordinary members see names/colors only; `userId` and `email`
     * are granted to MANAGE_MEMBERS viewers (their management actions need them);
     * guests see just their own entry. Null = viewer cannot see the room at all.
     */
    fun censorRoomMembers(room: Room): List<MembershipPayload>? {
        if (user == null || room.effectiveMembers.none { it.user eqid user }) return null
        val canManage = room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)
        val isProperViewer = user.type in setOf(UserType.ADMIN, UserType.MEMBER)
        return room.effectiveMembers.mapNotNull { m ->
            if (!isProperViewer && !(m.user eqid user)) return@mapNotNull null
            val target = m.user.deref()
            val presentation = getUserPresentation(target, user, UserPresentationContext.MEMBER_LIST, room)
            MembershipPayload(
                user = presentation.copy(userId = if (canManage) m.user else null),
                rowKey = memberRowKey(room, m),
                email = if (canManage) target?.email else null,
                isProperUser = target?.type?.isProper() ?: false,
                role = m.role,
                invitedVia = m.invitedVia,
                implied = m.implied,
                impliedFrom = m.impliedFrom,
            )
        }
    }

    private fun memberRowKey(room: Room, m: RoomMembership): String =
        MessageDigest.getInstance("SHA-256")
            .digest("member:${room.id}:${m.user.id}:${m.impliedFrom ?: ""}".encodeToByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            .take(16)

    fun censorRoomsDirectory() = state.rooms.mapValuesNotNull { censorRoomDirectory(it.value) }

    fun censorUsers() =
        state.users.mapValuesNotNull { censorUser(it.value) }

    /**
     * The "room/<id>/questions" topic payload (§4.2): the room's questions (own +
     * extension-provided effective ones, e.g. group-room aggregation), keyed by id.
     * Each question is censored by [censorQuestion] against its *own* room, so
     * cross-room effective questions keep exactly the visibility the viewer has
     * there. Counts travel on the "room/<id>/questions/counts" sibling
     * ([censorRoomQuestionCounts]) so count churn never re-sends the entities.
     * Null = viewer cannot see the room at all.
     */
    fun censorRoomQuestions(room: Room): Map<String, Question>? {
        if (user == null || room.effectiveMembers.none { it.user eqid user }) return null
        val refs = (room.questions + (room.effectiveQuestions?.map { it.question } ?: emptyList())).toSet()
        return refs.mapNotNull { it.deref() }
            .mapNotNull { censorQuestion(it) }
            .associateBy { it.id }
    }

    /**
     * The "room/<id>/questions/counts" topic payload: per-question counts for
     * exactly the questions visible in the room slice. The visibility set reuses
     * [censorRoomQuestions] — counts of a question the viewer cannot see must not
     * leak, and a question's visibility transition must move its counts in the
     * same frame as its entity.
     */
    fun censorRoomQuestionCounts(room: Room): Map<Ref<Question>, QuestionCounts>? =
        censorRoomQuestions(room)?.keys?.associate { id ->
            val ref = Ref<Question>(id)
            ref to QuestionCounts(
                predictions = state.predictionCount[ref] ?: 0,
                predictors = state.predictorCount[ref] ?: 0,
                comments = state.commentCount[ref] ?: 0,
            )
        }
}

/** The per-user "/state/self" topic payload (PERSISTENCE_SYNC_REDESIGN.md §4.2). */
fun selfState(sess: UserSession, transientData: TransientData): SelfState {
    val user = sess.user
    return SelfState(
        // Own user rides along: the client resolves Ref<User> (own-ref eqid checks)
        // against this — the users map left ordinary clients. Only the session
        // fields the client reads are projected out; the raw UserSession (session
        // id, expiry) never leaves the server, which also retires the old
        // expiryTime-pinning dedup hack (renewals no longer touch the payload).
        user = user?.copy(password = null),
        presenterInfo = sess.presenterInfo,
        followingPresentation = sess.followingPresentation,
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
    MEMBER_LIST,
}

fun getUserPresentation(user: User?, observer: User, context: UserPresentationContext, room: Room?): UserPresentation {
    ServerExtension.forEach { ext-> ext.overrideUserPresentation(user, observer, context, room)?.let { return it } }
    if (user == null) return UserPresentation(false, "(Deleted user)")
    val amIModerator = room?.hasPermission(observer, RoomPermission.MANAGE_MEMBERS) ?: false
    // Member list: email travels as its own MembershipPayload field, so the name slot
    // keeps the plain display name instead of the moderator email fallback.
    val displayName =
        if (context == UserPresentationContext.MEMBER_LIST) user.displayName
        else user.nick ?: (if (amIModerator) user.email else null) ?: "(Anonymous)"
    return UserPresentation(user.id == observer.id, displayName, color = stringToColor(user.id))
}