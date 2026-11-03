package tools.confido.application

import java.security.MessageDigest
import kotlinx.datetime.Instant
import tools.confido.payloads.responses.MembershipPayload
import tools.confido.rooms.*
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.user
import tools.confido.extensions.ServerExtension
import tools.confido.question.Question
import tools.confido.question.resolvedAttributionIn
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.utils.*
import tools.confido.users.User
import tools.confido.users.UserPresentation
import tools.confido.users.UserType

/**
 * Process-lifetime secret salting [StateCensor.memberRowKey]: without it the digest
 * pre-image is fully known, so any viewer holding the users directory could brute-force
 * rowKeys against user ids and de-anonymize member rows. The keys only serve client-side
 * list reconciliation, so stability within one server run is all that is needed.
 */
private val rowKeySalt: String = generateToken()

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
        // Room is visible if the user is an effective member (with any permission)
        // whose invite link still grants access (DISABLED_FULL revokes wholesale).
        // Which tabs/content they see is controlled by their effective permissions.
        val accessibleOwn = room.accessibleMemberships(user)
        if (user == null || accessibleOwn.isEmpty()) return null

        // Only the viewer's own (effective) membership travels on Room — it is what
        // client-side permission checks read. Other members' data is granted per
        // context by the room/<id>/members topic (censorRoomMembers) as presentations.
        val ownMembers = accessibleOwn.map { m ->
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
            // Alias map: full only for piercing viewers in rooms with no sealed
            // pierceable policy; everyone else sees just their own entry (the
            // "you appear as" cue). See pseudonymMapVisibleTo.
            pseudonymAssignments =
                if (room.pseudonymMapVisibleTo(user)) room.pseudonymAssignments
                else room.pseudonymAssignments.filterKeys { it == user.id },
            // The parent room may be invisible to this viewer; ship the resolved
            // default so client-side effectiveAttribution stays correct.
            _effectiveDefaultAttribution = room.resolvedDefaultAttribution,
            inviteLinks = if (user?.type in setOf(UserType.ADMIN, UserType.MEMBER)) room.inviteLinks.mapNotNull { censorInviteLink(room, it) } else emptyList(),
        )
        ServerExtension.forEach { censored = it.censorRoom(censored, user) }
        return censored
    }


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
        if (user == null || room.accessibleMemberships(user).isEmpty()) return null
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
            .digest("member:$rowKeySalt:${room.id}:${m.user.id}:${m.impliedFrom ?: ""}".encodeToByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            .take(16)

    fun censorRoomsDirectory() = state.rooms.mapValuesNotNull { censorRoomDirectory(it.value) }
}

/**
 * The permission class (§4.3): everything the class-pure censors depend on — the
 * viewer's user type plus, per room where the viewer is an effective member, the
 * viewer's effective permission set. Two viewers with an equal PermClass receive
 * byte-identical payloads on the shared topics, which is what makes the
 * per-(topic, class) view registry sound. Deliberately a small equality-friendly
 * value: the shared-topic machinery uses it both as a memo cutoff (class churn is
 * rare) and as a registry key.
 */
data class PermClass(
    val userType: UserType,
    val roomPerms: Map<Ref<Room>, Set<RoomPermission>>,
)

/**
 * Project the class onto [rooms] — the key-narrowing for room-scoped shared topics
 * (a global class fragments to near-per-user once membership vectors vary; the
 * projection collapses a topic's key to the rooms its censor actually reads).
 * Fail-closed by construction: the topic body receives the *projected* class, so a
 * room missing from the scope reads as "no permissions" — an under-projection
 * censors too much, visibly, and can never leak data between same-key viewers.
 */
fun PermClass.scopedTo(rooms: Set<Ref<Room>>): PermClass =
    copy(roomPerms = roomPerms.filterKeys { it in rooms })

/** Scope for question-instance topics: the question's own room — the key collapses
 *  to (user type, one permission set), a handful of values per deployment. */
fun questionTopicScope(cls: PermClass, qRef: Ref<Question>): PermClass =
    cls.scopedTo(setOfNotNull(serverState.questionRoom[qRef]))

/** Scope for the room questions/counts topics: the room *plus the rooms of its
 *  effective questions* — each effective question is censored against its own room,
 *  so group-room viewers with different child-room permissions legitimately differ. */
fun roomQuestionsTopicScope(cls: PermClass, rRef: Ref<Room>): PermClass {
    val effRooms = rRef.deref()?.effectiveQuestions
        ?.mapNotNull { serverState.questionRoom[it.question] }?.toSet() ?: emptySet()
    return cls.scopedTo(effRooms + rRef)
}

fun permClassOf(user: User): PermClass = PermClass(
    user.type,
    serverState.rooms.values.mapNotNull { room ->
        // accessibleMemberships (not raw effectiveMembers): a DISABLED_FULL invite
        // link revokes its invitees entirely, exactly as in Room.hasPermission.
        val memberships = room.accessibleMemberships(user)
        if (memberships.isEmpty()) null
        // Union across memberships (direct + implied) — hasPermission accepts any.
        // An entry with an empty set still records membership (room visibility).
        else room.ref to memberships.flatMap { room.memberEffectivePermissions(it) }.toSet()
    }.toMap()
)

/**
 * Censoring that is a pure function of the viewer's [PermClass] (§4.3) — the censors
 * behind the *shared* topics (question collections/instances, counts, users), computed
 * once per (topic, class) however many viewers subscribe. Per-viewer censoring (own
 * membership on Room, presentation self flags) stays on [StateCensor]. Anything added
 * here must read nothing viewer-specific beyond the class — otherwise same-class
 * viewers would receive each other's data.
 */
class ClassCensor(val cls: PermClass) {
    val state = serverState

    fun hasPermission(room: Room, permission: RoomPermission) =
        permission in (cls.roomPerms[room.ref] ?: emptySet())

    /** Effective membership in [room] — the entry exists even with an empty permission set. */
    fun isMember(room: Room) = room.ref in cls.roomPerms

    fun censorQuestion(q: Question): Question? {
        val room = state.questionRoom[q.ref]?.deref() ?: return null
        if (!hasPermission(room, RoomPermission.VIEW_QUESTIONS)) return null
        if (!q.visible && !hasPermission(room, RoomPermission.VIEW_HIDDEN_QUESTIONS)) return null

        var ret = q
        // Hide prepared resolutions if question is not set to resolved yet.
        if (!ret.resolutionVisible && !hasPermission(room, RoomPermission.VIEW_ALL_RESOLUTIONS)) {
            ret = ret.copy(resolution = null)
        }
        ServerExtension.forEach { ret = it.censorQuestion(ret, room, cls) }
        return ret
    }

    fun censorQuestions() = state.questions.mapValuesNotNull { censorQuestion(it.value) }

    // The users directory is only served to proper users (gated in the topic body);
    // guests' identity needs are covered by presentations embedded in payloads (§5.3).
    // Two tiers: admins get the administrative fields their directory renders; members
    // get only what the add-member search reads (id/type/nick/email) — the rest is
    // pinned to constants (audit of 2026-08-16: lastLoginAt is colleague-activity
    // data, createdAt/emailVerified/active administrative metadata; no member surface
    // consumes any of them).
    fun censorUser(u: User): User? = when (cls.userType) {
        // uiSettings are owner-only (they ride the self slice); no surface reads
        // other users' preferences, admins included.
        UserType.ADMIN -> u.copy(password = null, uiSettings = emptyMap())
        UserType.MEMBER -> u.copy(
            password = null,
            emailVerified = false,
            createdAt = Instant.fromEpochSeconds(0),
            lastLoginAt = null,
            active = true,
            uiSettings = emptyMap(),
        )
        else -> null
    }

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
        if (!isMember(room)) return null
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
        // At most one live link per user (issuing one deletes the older ones); a link for
        // the current address is a re-verification, not a change.
        myPendingEmail = user?.let { u ->
            tools.confido.store.verificationLinkStore.asMap.values
                .firstOrNull { it.user eqid u && !it.isExpired() && it.email != u.email?.lowercase() }
                ?.email
        },
        presenterWindowActive = transientData.activePresenterWindows > 0,
    )
}

enum class UserPresentationContext {
    COMMENT_AUTHOR,
    SCOREBOARD,
    INDIVIDUAL_PREDICTION,
    MEMBER_LIST,
}

/**
 * The single chokepoint for identity display. [question] selects the attribution
 * policy (its effective policy; null = the room default, e.g. scoreboard or room
 * comments); under a masked (pierceable) policy the *canonical* [UserPresentation.displayName]
 * is always the masked form — piercing observers get the real identity only as the
 * [UserPresentation.realIdentity] annotation, so audience-facing surfaces (presenter
 * views) can never project a real name by construction.
 *
 * The member list is exempt: membership is never secret, only activity attribution
 * is. Alias annotations there come from the (censored) [Room.pseudonymAssignments].
 */
fun getUserPresentation(user: User?, observer: User, context: UserPresentationContext, room: Room?,
                        question: Question? = null): UserPresentation {
    ServerExtension.forEach { ext-> ext.overrideUserPresentation(user, observer, context, room, question)?.let { return it } }
    if (user == null) return UserPresentation(false, "(Deleted user)")

    val policy = question?.resolvedAttributionIn(room) ?: room?.resolvedDefaultAttribution ?: AttributionPolicy()
    if (room != null && policy.level.pierceable && context != UserPresentationContext.MEMBER_LIST) {
        val realIdentity = if (policy.pierceableBy(observer, room))
            user.nick ?: user.email ?: "(Anonymous)" else null
        return when (policy.level) {
            AttributionLevel.PSEUDONYM -> {
                val alias = room.aliasFor(user.id)
                UserPresentation(
                    isMyself = user.id == observer.id,
                    // Missing alias (assignment race) falls back to an unlinkable-looking
                    // placeholder with a stable per-user color, never to the real name.
                    displayName = alias ?: "Anonymous participant",
                    color = stringToColor(alias ?: (room.id + user.id)),
                    realIdentity = realIdentity,
                )
            }
            // AGGREGATE_ONLY is strictly stricter than ANONYMOUS: its comments
            // (the only individual content it has) are masked the same way.
            AttributionLevel.ANONYMOUS, AttributionLevel.AGGREGATE_ONLY -> UserPresentation(
                isMyself = user.id == observer.id,
                displayName = "Anonymous",
                // No color: a stable color IS linkage.
                color = null,
                realIdentity = realIdentity,
            )
            else -> throw IllegalStateException("unreachable: non-pierceable level")
        }
    }

    val amIModerator = room?.hasPermission(observer, RoomPermission.MANAGE_MEMBERS) ?: false
    // Member list: email travels as its own MembershipPayload field, so the name slot
    // keeps the plain display name instead of the moderator email fallback.
    val displayName =
        if (context == UserPresentationContext.MEMBER_LIST) user.displayName
        else user.nick ?: (if (amIModerator) user.email else null) ?: "(Anonymous)"
    return UserPresentation(user.id == observer.id, displayName, color = stringToColor(user.id))
}