package tools.confido.payloads.responses

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.rooms.Room
import tools.confido.rooms.RoomColor

/**
 * One entry of a dashboard section topic (`dashboard/<section>`). Carries its own display
 * data (names, color) so the dashboard does not need the workspace-wide question
 * collection.
 *
 * Sections are *candidate* lists: every time comparison ("closes soon", staleness,
 * recency windows, overdue) happens on the client against the raw instants carried
 * here. This keeps the server views pure functions of state — they wake only on
 * actual changes and need no clock input — while each dashboard visit evaluates
 * the cutoffs against a fresh "now" (a new subscription frames current data).
 */
@Serializable
data class DashboardItem(
    val question: Ref<Question>,
    val questionName: String,
    val room: Ref<Room>,
    val roomName: String,
    val roomColor: RoomColor,
    /** Effective close time, when scheduled (waiting section). */
    val closeTime: Instant? = null,
    /** Unix timestamp of the viewer's latest prediction (revisit section). */
    val predictionTs: Int? = null,
    /** Formatted resolution value (resolved section). Scores are not included —
     * computing them reads the prediction-history archive, which a pure view must
     * not do; the client joins them from the scores API it fetches anyway. */
    val resolution: String? = null,
    val resolvedAt: Instant? = null,
    val moderation: DashboardModerationKind? = null,
    /** Scheduled resolution time (moderation candidates; overdue decided client-side). */
    val resolveDue: Instant? = null,
) {
    val url get() = Room.urlPrefix(room.id) + Question.urlPrefix(question.id)
}

@Serializable
enum class DashboardModerationKind {
    /** The question is a draft that has not been opened yet. */
    DRAFT,

    /** The question has a resolution scheduled ([DashboardItem.resolveDue]) but is
     * not resolved; the client surfaces it once the schedule has passed. */
    RESOLUTION_DUE,
}
