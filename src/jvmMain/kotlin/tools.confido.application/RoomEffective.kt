package rooms

import tools.confido.extensions.ServerExtension
import tools.confido.refs.ref
import tools.confido.state.appConfig
import tools.confido.state.serverState
import users.UserType

actual val Room.effectiveMembers: List<RoomMembership>
    get() {
        // Start with extension-provided effective members, or raw members
        val base = ServerExtension.enabled.firstNotNullOfOrNull {
            it.effectiveMembers(this)
        } ?: this.members

        // Inject admin users as implied owners so they are subject to
        // memberEffectivePermissions like everyone else (e.g. group rooms
        // correctly strip question-related permissions from admins too).
        val memberUserIds = base.map { it.user.id }.toSet()
        val ownerRole = appConfig.roleById("owner") ?: return base
        val adminMembers = serverState.users.values
            .filter { it.type == UserType.ADMIN && it.id !in memberUserIds }
            .map { RoomMembership(it.ref, ownerRole, implied = true, impliedFrom = "Admin") }

        return if (adminMembers.isEmpty()) base else base + adminMembers
    }

actual val Room.effectiveQuestions: List<EffectiveQuestion>?
    get() = ServerExtension.enabled.firstNotNullOfOrNull {
        it.effectiveQuestions(this)
    }

actual fun Room.memberEffectivePermissions(membership: RoomMembership): Set<RoomPermission> {
    // 1. Let extensions override (or start with role permissions)
    val hookResult = ServerExtension.enabled.firstNotNullOfOrNull {
        it.effectivePermissions(this, membership)
    }
    val perms = hookResult ?: membership.role.effectivePermissions

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
