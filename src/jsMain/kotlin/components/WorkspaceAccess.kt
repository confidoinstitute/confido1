package components

import hooks.useSubscription
import payloads.responses.WSData
import react.use
import rooms.Room
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.state.QuestionsState
import tools.confido.state.Topics

/**
 * Workspace-level data accessors for the cross-room surfaces (dashboard, search,
 * calibration, scores, room list): surface-local subscriptions of the `rooms`
 * directory and `questions` topics, following the users-topic precedent — after
 * the eviction these subscriptions are the only source of cross-room data, and
 * surfaces that display cross-room data pay for exactly that. MultiplexWS dedup
 * makes overlapping subscriptions (incl. the transitional root ones) free, and
 * the sender's atomic frames make the duplication consistent duplication.
 *
 * Real hooks — call unconditionally at component top level, then join/look up
 * with the pure helpers below (safe in loops and nested render functions).
 */
fun useRooms(): Map<String, Room> {
    val sub = useSubscription(Topics.rooms())
    // Transitional bridge while the directory is still root-subscribed; dies
    // with the AppState facade at the eviction.
    return (sub as? WSData)?.data ?: use(AppStateContext)?.rooms ?: emptyMap()
}

fun useWorkspaceQuestions(): QuestionsState {
    val sub = useSubscription(Topics.questions())
    return (sub as? WSData)?.data ?: use(AppStateContext)?.questionsState ?: QuestionsState()
}

/** The room a question belongs to, resolved against a subscribed directory. */
fun Map<String, Room>.roomOf(question: Question): Room? =
    values.firstOrNull { question.ref in it.questions }
