package tools.confido.application.routes

import io.ktor.server.routing.*
import tools.confido.api.Api
import tools.confido.application.ClassCensor
import tools.confido.application.StateCensor
import tools.confido.application.permClassOf
import tools.confido.application.sessions.userSession
import tools.confido.payloads.responses.QuestionHitPayload
import tools.confido.payloads.responses.RoomHitPayload
import tools.confido.payloads.responses.SearchResponse
import tools.confido.refs.ref
import tools.confido.search.SearchScope
import tools.confido.search.searchWorkspace

/** Bounds on one response; the match scoring already puts the best hits first. */
private const val MAX_QUESTION_HITS = 50
private const val MAX_ROOM_HITS = 20

fun searchRoutes(routing: Routing) = routing.apply {
    // Workspace search runs over exactly the state the client used to search locally:
    // the viewer-censored rooms directory and questions map (the same censors that
    // back the "rooms" and "questions" topics), so moving the matching server-side
    // changes latency, not visibility.
    apiST(Api.search) { req ->
        call.withUser {
            val sessionData = call.userSession ?: unauthorized("Not logged in.")
            val rooms = StateCensor(sessionData).censorRoomsDirectory()
            val questions = ClassCensor(permClassOf(user)).censorQuestions()
            val results = searchWorkspace(rooms, questions, req.query, req.scope.ifEmpty { SearchScope.default })
            SearchResponse(
                terms = results.terms,
                questions = results.questions.take(MAX_QUESTION_HITS).map {
                    QuestionHitPayload(it.question, it.room.ref, it.descriptionSnippet, it.matchedTags)
                },
                rooms = results.rooms.take(MAX_ROOM_HITS).map {
                    RoomHitPayload(it.room.ref, it.descriptionSnippet)
                },
            )
        }
    }
}
