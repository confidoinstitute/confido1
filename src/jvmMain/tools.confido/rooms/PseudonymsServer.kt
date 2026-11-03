package tools.confido.rooms

import kotlin.random.Random
import tools.confido.refs.deref
import tools.confido.state.serverState
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

private val Room.parentEntity: Room? get() = parentRoom?.let { serverState.rooms[it.id] }

/** Pseudonymous content can exist directly in this room. */
private fun Room.needsPseudonymsLocally(): Boolean =
    resolvedDefaultAttribution.level == AttributionLevel.PSEUDONYM ||
    !pseudonymPool.isNullOrEmpty() ||
    questions.any { it.deref()?.attribution?.level == AttributionLevel.PSEUDONYM }

/**
 * Whether alias assignments must be maintained on THIS room's map. A group also
 * assigns when any namespace-sharing (non-[Room.separatePseudonyms]) child needs
 * aliases: children resolve read-through against the parent map, so covering all
 * parent members here means a new implied member is aliased with no child touch
 * (no cascade needed).
 */
fun Room.needsPseudonyms(): Boolean =
    needsPseudonymsLocally() ||
    (isGroup && childRooms.any { !it.separatePseudonyms && it.needsPseudonymsLocally() })

/**
 * Resolve [userId]'s alias in this room's namespace. Inherit-mode children read
 * the parent map FIRST (shared identity dominates; stale local entries from an
 * earlier per-child period are shadowed), then their own map ("extend" entries
 * for members the parent doesn't know). Owned namespaces read only their own map.
 */
fun Room.aliasFor(userId: String): String? = aliasFor(userId, parentEntity)

/**
 * Top up [Room.pseudonymAssignments] so every current *effective* member (implied
 * members included) resolves to a stable alias. Runs as a `beforeEntitySave` hook
 * on the room store.
 *
 * Assignments are only ever *added*, never removed or reshuffled: an alias is
 * part of the attribution promise, so it must survive pool edits, membership
 * churn (a departed member's alias keeps their existing content linked and is
 * never reissued) and feature toggling.
 *
 * Namespace rules: an inherit-mode child assigns only members its parent map does
 * not cover; the pool reads through to the namespace owner's. Uniqueness is
 * hierarchy-wide — an alias string never means two different people anywhere in
 * one hierarchy (parent, siblings, children; any mode), so cross-room sightings
 * of a name are never false linkage.
 */
fun ensurePseudonymAssignments(room: Room): Room {
    if (!room.needsPseudonyms()) return room
    val parent = room.parentEntity

    val memberIds = room.effectiveMembers.map { it.user.id }.distinct()
    val covered = room.pseudonymAssignments.keys +
        (if (!room.ownsPseudonymNamespace) parent?.pseudonymAssignments?.keys ?: emptySet()
         else emptySet())
    val unassigned = memberIds.filter { it !in covered }.sorted()
    if (unassigned.isEmpty()) return room

    val hierarchyUsed = buildSet {
        addAll(room.pseudonymAssignments.values)
        parent?.let { p ->
            addAll(p.pseudonymAssignments.values)
            p.childRooms.forEach { addAll(it.pseudonymAssignments.values) }
        }
        if (room.isGroup) room.childRooms.forEach { addAll(it.pseudonymAssignments.values) }
    }

    val poolOwner = if (room.ownsPseudonymNamespace) room else (parent ?: room)
    val customPool = poolOwner.pseudonymPool.orEmpty()
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val rnd = Random(room.id.hashCode())
    val available = (customPool.shuffled(rnd) + generatedAliasPool.shuffled(rnd))
        .distinct().filter { it !in hierarchyUsed }

    val newAssignments = room.pseudonymAssignments.toMutableMap()
    unassigned.zip(available).forEach { (uid, alias) -> newAssignments[uid] = alias }
    return room.copy(pseudonymAssignments = newAssignments)
}

/**
 * Re-draw EVERY alias in this room's map from the current pool (custom names
 * first, then generated). Privacy-neutral — a bijective relabeling: all content
 * re-labels consistently and linkage is preserved — but disruptive (learned
 * aliases, in-text mentions and old exports go stale), so it sits behind an
 * explicit owner action. Covers all existing keys (departed members included, so
 * their content stays labeled) plus current effective members; hierarchy-wide
 * uniqueness holds against parent/sibling/child maps. Unseeded shuffle on
 * purpose: a redraw must actually change the outcome.
 */
fun redrawPseudonymAssignments(room: Room): Room {
    val keys = (room.pseudonymAssignments.keys + room.effectiveMembers.map { it.user.id })
        .distinct().sorted()
    val parent = room.parentEntity
    val othersUsed = buildSet {
        parent?.let { p ->
            addAll(p.pseudonymAssignments.values)
            p.childRooms.filter { it.id != room.id }.forEach { addAll(it.pseudonymAssignments.values) }
        }
        if (room.isGroup) room.childRooms.forEach { addAll(it.pseudonymAssignments.values) }
    }
    val customPool = room.pseudonymPool.orEmpty()
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val available = (customPool.shuffled() + generatedAliasPool.shuffled())
        .distinct().filter { it !in othersUsed }
    return room.copy(pseudonymAssignments = keys.zip(available).toMap())
}

/**
 * Whether the full alias map may travel to [user]. Requires
 * [RoomPermission.VIEW_REAL_IDENTITIES], and additionally no *sealed* pierceable
 * policy anywhere sharing this map's namespace (this room's default or question
 * overrides; for a group also its inherit-mode children's — their content resolves
 * against this map, so one sealed sharer means own-entry-only for everyone).
 * Per-question piercing still works through `UserPresentation.realIdentity`,
 * which honors each question's own policy.
 */
fun Room.pseudonymMapVisibleTo(user: User?): Boolean {
    if (!hasPermission(user, RoomPermission.VIEW_REAL_IDENTITIES)) return false
    fun sealedIn(r: Room): Boolean =
        (listOf(r.resolvedDefaultAttribution) + r.questions.mapNotNull { it.deref()?.attribution })
            .any { it.level.pierceable && !it.moderatorAccess }
    if (sealedIn(this)) return false
    if (isGroup && childRooms.any { !it.separatePseudonyms && sealedIn(it) }) return false
    return true
}
