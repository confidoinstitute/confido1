package tools.confido.store

import tools.confido.question.Question
import tools.confido.question.effectiveAttribution
import tools.confido.question.resolvedAttributionIn
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.rooms.AttributionPolicy
import tools.confido.rooms.resolvedDefaultAttribution
import tools.confido.rooms.Room
import tools.confido.rooms.strictestOf

// Promise-floor stamping: called from the activity-insert choke points
// (addPrediction, MongoCommentArchive.insert) with the policy in force at
// authoring time, so the ratchet can later compare proposed policies against
// what the activity was actually promised — not against "any activity exists".
// Runs inside the insert's transaction scope (modify stages into the ambient
// txn). Deliberately never fires on boot/load paths: re-stamping old activity
// with the *current* policy would wrongly raise floors past what was promised.

private fun combineFloor(old: AttributionPolicy?, policy: AttributionPolicy): AttributionPolicy? {
    // An OPEN, moderator-accessible floor never binds (nothing reveals more than
    // it) — keep such floors as null to avoid write churn on every open question.
    if (old == null && policy == AttributionPolicy()) return null
    return old?.let { strictestOf(it, policy) } ?: policy
}

/** Raise [qRef]'s promise floor to cover its current effective policy. */
suspend fun stampQuestionAttributionFloor(qRef: Ref<Question>) {
    val q = qRef.deref() ?: return
    val newFloor = combineFloor(q.attributionFloor, q.effectiveAttribution) ?: return
    if (newFloor == q.attributionFloor) return
    questionStore.modify(qRef) { it.copy(attributionFloor = newFloor) }
}

/** Raise [rRef]'s promise floor (room comments) to cover its current (parent-aware)
 *  default policy. */
suspend fun stampRoomAttributionFloor(rRef: Ref<Room>) {
    val room = rRef.deref() ?: return
    val newFloor = combineFloor(room.attributionFloor, room.resolvedDefaultAttribution) ?: return
    if (newFloor == room.attributionFloor) return
    roomStore.modify(rRef) { it.copy(attributionFloor = newFloor) }
}
