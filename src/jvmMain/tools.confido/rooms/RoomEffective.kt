package tools.confido.rooms

import tools.confido.extensions.ServerExtension
import tools.confido.refs.ref
import tools.confido.state.appConfig
import tools.confido.state.serverState
import tools.confido.refs.deref
import tools.confido.users.UserType

/** Child rooms of a group room. */
val Room.childRooms: List<Room> get() =
    serverState.rooms.values.filter { it.parentRoom?.id == id }

/** Server-side parent-aware default attribution (raw store entities lack the
 *  censor-populated `_effectiveDefaultAttribution`; this resolves live). */
val Room.resolvedDefaultAttribution: AttributionPolicy
    get() = effectiveDefaultAttributionIn(parentRoom?.let { serverState.rooms[it.id] })

/**
 * The meet of every promise floor bound by this room's default attribution: its
 * own (room comments), its inheriting questions', and — for groups — the ratchet
 * floors of children inheriting the default. A proposed default violates the
 * ratchet iff it [AttributionPolicy.revealsMoreThan] this meet (iff it reveals
 * more than at least one component).
 */
val Room.attributionRatchetFloor: AttributionPolicy? get() {
    val components = listOfNotNull(attributionFloor) +
        questions.mapNotNull { it.deref()?.takeIf { q -> q.attribution == null }?.attributionFloor } +
        (if (isGroup) childRooms.filter { it.defaultAttribution == null }
            .mapNotNull { it.attributionRatchetFloor } else emptyList())
    return components.reduceOrNull(::strictestOf)
}

/** Core implied-membership rule for rooms in a group: parent-group members become
 *  implied members of the child. Null for rooms without a parent. Extensions can
 *  preempt this via [ServerExtension.effectiveMembers]; an override that only wants
 *  to tweak the result can call this and adjust. */
fun defaultGroupEffectiveMembers(room: Room): List<RoomMembership>? {
    val parent = room.parentRoom?.let { serverState.rooms[it.id] } ?: return null

    val directUserIds = room.members.map { it.user.id }.toSet()
    val impliedMembers = parent.members
        .filter { it.user.id !in directUserIds }
        // The invite-link accessibility check (DISABLED_FULL revocation) must
        // happen HERE, against the parent — the link id resolves only in the
        // room that owns the membership; checked in the child it would silently
        // fail open. The copy drops invitedVia for the same reason: on the
        // implied membership it would be a dangling id no child surface can
        // resolve, and `findLink(...)?.canAccess ?: true` is only sound if
        // invitedVia, when set, refers to a link of the carrying room.
        .filter { parent.findLink(it.invitedVia)?.canAccess ?: true }
        .map { it.copy(implied = true, impliedFrom = parent.name, invitedVia = null) }

    return room.members + impliedMembers
}

/** Core rule for group rooms: they have no questions of their own, so all
 *  question-related permissions are stripped. Null for non-group rooms. */
fun defaultGroupEffectivePermissions(room: Room, membership: RoomMembership): Set<RoomPermission>? {
    if (!room.isGroup) return null
    return membership.role.effectivePermissions - RoomPermission.QUESTION_PERMISSIONS
}

/** Core rule for group rooms: aggregate child rooms' questions — hidden from the
 *  group's question list but included in its scoreboard. Null for non-group rooms. */
fun defaultGroupEffectiveQuestions(room: Room): List<EffectiveQuestion>? {
    if (!room.isGroup) return null
    return room.childRooms.flatMap { child ->
        child.questions.map { qRef ->
            EffectiveQuestion(
                question = qRef,
                sourceRoom = child.ref,
                visibleInList = false,       // don't show child questions in group's question list
                includedInScoring = true,    // include in aggregated group scoreboard
            )
        }
    }
}

actual val Room.effectiveMembers: List<RoomMembership>
    get() {
        // Extension hooks preempt the core group rule, which preempts raw members
        val base = ServerExtension.enabled.firstNotNullOfOrNull {
            it.effectiveMembers(this)
        } ?: defaultGroupEffectiveMembers(this) ?: this.members

        // Inject admin users as implied owners so they are subject to
        // memberEffectivePermissions like everyone else (e.g. group rooms
        // correctly strip question-related permissions from admins too).
        // A direct membership with a lesser role must not shadow this: an admin
        // who demoted themselves would otherwise lose the power to undo it.
        val ownerRole = appConfig.roleById("owner") ?: return base
        val ownerUserIds = base.filter { it.role == ownerRole }.map { it.user.id }.toSet()
        val adminMembers = serverState.users.values
            .filter { it.type == UserType.ADMIN && it.id !in ownerUserIds }
            .map { RoomMembership(it.ref, ownerRole, implied = true, impliedFrom = "Admin") }

        return if (adminMembers.isEmpty()) base else base + adminMembers
    }

actual val Room.effectiveQuestions: List<EffectiveQuestion>?
    get() = ServerExtension.enabled.firstNotNullOfOrNull {
        it.effectiveQuestions(this)
    } ?: defaultGroupEffectiveQuestions(this)

actual fun Room.memberEffectivePermissions(membership: RoomMembership): Set<RoomPermission> {
    // 1. Let extensions override, then the core group rule, then role permissions
    val hookResult = ServerExtension.enabled.firstNotNullOfOrNull {
        it.effectivePermissions(this, membership)
    }
    val perms = hookResult
        ?: defaultGroupEffectivePermissions(this, membership)
        ?: membership.role.effectivePermissions

    // 2. Apply core dynamic rules based on room config
    return applyCorePermissionRules(this, perms)
}

/** Apply core dynamic permission rules based on room configuration.
 *  These run after extension hooks, removing permissions that don't apply
 *  given the room's current settings. */
private fun applyCorePermissionRules(room: Room, perms: Set<RoomPermission>): Set<RoomPermission> {
    val result = perms.toMutableSet()

    // Scoring visibility: VIEW_SCORES is granted by roles but removed when
    // the room's scoreboard config doesn't allow it.
    val sbm = room.scoring?.scoreboardMode ?: ScoreboardMode.NONE
    when (sbm) {
        ScoreboardMode.NONE -> {
            result.remove(RoomPermission.VIEW_SCORES)
        }
        ScoreboardMode.PRIVATE -> {
            if (RoomPermission.VIEW_ALL_SCORES !in perms) {
                result.remove(RoomPermission.VIEW_SCORES)
            }
        }
        ScoreboardMode.PUBLIC -> {
            // VIEW_SCORES stays as granted by the role
        }
    }

    return result
}
