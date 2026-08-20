package tools.confido.payloads.responses

import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.rooms.Room
import tools.confido.search.Snippet

/** A question hit: the censored question entity itself (the result list renders full
 * question rows) plus a room *ref* — the client resolves it against its subscribed
 * rooms directory rather than paying for a copy of the room per hit. */
@Serializable
data class QuestionHitPayload(
    val question: Question,
    val room: Ref<Room>,
    val descriptionSnippet: Snippet? = null,
    val matchedTags: List<String> = emptyList(),
)

@Serializable
data class RoomHitPayload(
    val room: Ref<Room>,
    val descriptionSnippet: Snippet? = null,
)

/** Hits are sorted server-side (best first); [terms] echo the parsed query for
 * client-side highlighting. */
@Serializable
data class SearchResponse(
    val terms: List<String>,
    val questions: List<QuestionHitPayload>,
    val rooms: List<RoomHitPayload>,
) {
    val isEmpty get() = questions.isEmpty() && rooms.isEmpty()
}
