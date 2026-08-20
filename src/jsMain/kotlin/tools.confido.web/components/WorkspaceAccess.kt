package tools.confido.web.components

import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import tools.confido.rooms.Room
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.state.Topics

/**
 * Workspace-level data accessors for the cross-room surfaces (dashboard, room
 * list, question→room resolution): a surface-local subscription of the `rooms`
 * directory. MultiplexWS dedup makes overlapping subscriptions free, and the
 * sender's atomic frames make the duplication consistent duplication.
 *
 * The workspace-wide questions map is retired: cross-room question data travels
 * on purpose-built payloads (dashboard sections, search results, calibration
 * detail) and by-id access goes through `useQuestion` (question/<id>).
 *
 * Real hooks — call unconditionally at component top level, then join/look up
 * with the pure helpers below (safe in loops and nested render functions).
 */
fun useRooms(): Map<String, Room> {
    val sub = useSubscription(Topics.rooms())
    return (sub as? WSData)?.data ?: emptyMap()
}

/** The room a question belongs to, resolved against a subscribed directory. */
fun Map<String, Room>.roomOf(question: Question): Room? =
    values.firstOrNull { question.ref in it.questions }
