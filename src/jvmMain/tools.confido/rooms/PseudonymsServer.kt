package tools.confido.rooms

import kotlin.random.Random
import tools.confido.refs.deref
import tools.confido.users.User

// Built-in alias pool: neutral adjective+animal pairs (~576 combinations), used
// whenever no custom pool is configured and to top up a custom pool that is
// shorter than the member count.
private val aliasAdjectives = listOf(
    "Amber", "Azure", "Bold", "Brisk", "Calm", "Clever", "Coral", "Crimson",
    "Daring", "Deft", "Golden", "Ivory", "Jade", "Keen", "Lively", "Mellow",
    "Nimble", "Olive", "Quiet", "Silver", "Swift", "Teal", "Violet", "Witty",
)
private val aliasAnimals = listOf(
    "Badger", "Bison", "Crane", "Dolphin", "Falcon", "Fox", "Gazelle", "Heron",
    "Ibex", "Jackal", "Kestrel", "Lynx", "Marten", "Newt", "Otter", "Panda",
    "Quail", "Raven", "Seal", "Tapir", "Urchin", "Vole", "Walrus", "Yak",
)
internal val generatedAliasPool: List<String> by lazy {
    aliasAdjectives.flatMap { adj -> aliasAnimals.map { "$adj $it" } }
}

/** Whether the room has (or may soon serve) pseudonymous content, so alias
 *  assignments must be maintained for its members. */
fun Room.needsPseudonyms(): Boolean =
    defaultAttribution.level == AttributionLevel.PSEUDONYM ||
    !pseudonymPool.isNullOrEmpty() ||
    questions.any { it.deref()?.attribution?.level == AttributionLevel.PSEUDONYM }

/**
 * Top up [Room.pseudonymAssignments] so every current member has a stable alias.
 * Runs as a `beforeEntitySave` hook on the room store, so member churn, pool
 * edits and policy changes all pass through it (question-level policy changes
 * trigger a room touch from the question routes).
 *
 * Assignments are only ever *added*, never removed or reshuffled: an alias is
 * part of the attribution promise, so it must survive pool edits, membership
 * churn (a departed member's alias keeps their existing content linked and is
 * never reissued) and feature toggling. Custom pool names are drawn first
 * (shuffled deterministically per room so assignment order is not guessable
 * from the curated list), then generated ones.
 */
fun ensurePseudonymAssignments(room: Room): Room {
    if (!room.needsPseudonyms()) return room
    val unassigned = room.members.map { it.user.id }.distinct()
        .filter { it !in room.pseudonymAssignments }.sorted()
    if (unassigned.isEmpty()) return room

    val customPool = room.pseudonymPool.orEmpty()
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val used = room.pseudonymAssignments.values.toSet()
    val rnd = Random(room.id.hashCode())
    val available = (customPool.shuffled(rnd) + generatedAliasPool.shuffled(rnd))
        .distinct().filter { it !in used }

    val newAssignments = room.pseudonymAssignments.toMutableMap()
    unassigned.zip(available).forEach { (uid, alias) -> newAssignments[uid] = alias }
    return room.copy(pseudonymAssignments = newAssignments)
}

/**
 * Whether the full alias map may travel to [user]. Requires
 * [RoomPermission.VIEW_REAL_IDENTITIES], and additionally no *sealed* pierceable
 * policy anywhere in the room (default or question override): the map is
 * room-level state, so one sealed pseudonymous question means everyone gets
 * own-entry-only censoring — per-question piercing still works through
 * `UserPresentation.realIdentity`, which honors each question's own policy.
 */
fun Room.pseudonymMapVisibleTo(user: User?): Boolean {
    if (!hasPermission(user, RoomPermission.VIEW_REAL_IDENTITIES)) return false
    val policies = listOf(defaultAttribution) + questions.mapNotNull { it.deref()?.attribution }
    return policies.none { it.level.pierceable && !it.moderatorAccess }
}
