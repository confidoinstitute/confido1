package tools.confido.application.routes

import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import tools.confido.application.sessions.user
import tools.confido.application.sessions.userSession
import tools.confido.payloads.responses.DashboardItem
import tools.confido.payloads.responses.DashboardModerationKind
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.*
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.state.Topics
import tools.confido.state.serverState
import tools.confido.store.*
import tools.confido.users.User
import tools.confido.utils.TopicSpec0

/**
 * Cap on each section's candidate list. The client applies its own display caps
 * and time cutoffs; this only bounds the payload for pathological workspaces.
 */
private const val SECTION_CAP = 30

private fun baseItem(room: Room, q: Question) = DashboardItem(
    question = q.ref,
    questionName = q.name,
    room = room.ref,
    roomName = room.name,
    roomColor = room.color,
)

private fun openedAt(q: Question): Instant? =
    q.stateHistory.filter { it.newState == QuestionState.OPEN }.maxOfOrNull { it.at }

private fun resolvedAt(q: Question): Instant? =
    q.stateHistory.filter { it.newState == QuestionState.RESOLVED }.maxOfOrNull { it.at }

/** The viewer's visible (room, question) pairs across the workspace — the
 *  dashboard-side analogue of [tools.confido.application.ClassCensor.censorQuestion]'s
 *  visibility rules, minus the resolution stripping (sections carry resolutions only
 *  for RESOLVED questions, where they are public by definition). */
private fun visibleEntries(user: User): List<Pair<Room, Question>> =
    serverState.rooms.values
        .filter { it.accessibleMemberships(user).isNotEmpty() && it.hasPermission(user, RoomPermission.VIEW_QUESTIONS) }
        .flatMap { room ->
            room.questions.mapNotNull { it.deref() }
                .filter { it.visible || room.hasPermission(user, RoomPermission.VIEW_HIDDEN_QUESTIONS) }
                .map { room to it }
        }

private fun waitingSection(user: User): List<DashboardItem> =
    visibleEntries(user)
        .filter { (_, q) -> q.state == QuestionState.OPEN && serverState.userPred[q.ref]?.get(user.ref) == null }
        .sortedByDescending { (_, q) -> openedAt(q) ?: Instant.DISTANT_PAST }
        .take(SECTION_CAP)
        .map { (room, q) -> baseItem(room, q).copy(closeTime = q.effectiveScheduleIn(room).close) }

private fun revisitSection(user: User): List<DashboardItem> =
    visibleEntries(user)
        .mapNotNull { (room, q) ->
            if (q.state != QuestionState.OPEN) return@mapNotNull null
            val pred = serverState.userPred[q.ref]?.get(user.ref) ?: return@mapNotNull null
            Triple(room, q, pred.ts)
        }
        .sortedBy { (_, _, ts) -> ts }
        .take(SECTION_CAP)
        .map { (room, q, ts) -> baseItem(room, q).copy(predictionTs = ts) }

private fun resolvedSection(user: User): List<DashboardItem> =
    visibleEntries(user)
        .mapNotNull { (room, q) ->
            if (q.state != QuestionState.RESOLVED) return@mapNotNull null
            val resolution = q.resolution ?: return@mapNotNull null
            val at = resolvedAt(q) ?: return@mapNotNull null
            Triple(room, q, at) to resolution
        }
        .sortedByDescending { (t, _) -> t.third }
        .take(SECTION_CAP)
        .map { (t, resolution) ->
            baseItem(t.first, t.second).copy(resolution = resolution.format(), resolvedAt = t.third)
        }

private fun moderationSection(user: User): List<DashboardItem> {
    val rooms = serverState.rooms.values.filter {
        it.accessibleMemberships(user).isNotEmpty() && it.hasPermission(user, RoomPermission.MANAGE_QUESTIONS)
    }
    val entries = rooms.flatMap { room -> room.questions.mapNotNull { it.deref() }.map { room to it } }
    val due = entries
        .mapNotNull { (room, q) ->
            if (q.state != QuestionState.OPEN && q.state != QuestionState.CLOSED) return@mapNotNull null
            val resolveDue = q.effectiveScheduleIn(room).resolve ?: return@mapNotNull null
            baseItem(room, q).copy(moderation = DashboardModerationKind.RESOLUTION_DUE, resolveDue = resolveDue)
        }
        .sortedBy { it.resolveDue }
    val drafts = entries
        .filter { (_, q) -> q.state == QuestionState.DRAFT }
        .map { (room, q) -> baseItem(room, q).copy(moderation = DashboardModerationKind.DRAFT) }
    return (due + drafts).take(SECTION_CAP)
}

fun dashboardRoutes(routing: Routing) = routing.apply {
    // The "dashboard/<section>" section topics: per-user candidate lists (see DashboardItem)
    // behind the dashboard's attention feed. One view per section so each frames and
    // dedups independently — resolution churn never re-sends the waiting list. All
    // sections share the same wake set (they read the same slices), so the split
    // saves wire traffic, not recomputation.
    fun <T> Routing.dashboardSection(spec: TopicSpec0<T>, compute: (User) -> T) =
        viewWS(spec) {
            if (call.userSession?.user == null) unauthorized("Not logged in.")
            combineViews(
                roomStore.state.asView(),
                questionStore.state.asView(),
                userStore.state.asView(),
                latestPredictions.state.asView(),
            ).map {
                val user = call.userSession?.user ?: unauthorized("Not logged in.")
                compute(user)
            }
        }

    dashboardSection(Topics.dashboardWaiting, ::waitingSection)
    dashboardSection(Topics.dashboardRevisit, ::revisitSection)
    dashboardSection(Topics.dashboardResolved, ::resolvedSection)
    dashboardSection(Topics.dashboardModeration, ::moderationSection)
}
