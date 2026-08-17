package tools.confido.web.components.redesign.search

import tools.confido.rooms.Room
import tools.confido.question.Question

/**
 * Which parts of the workspace a query is matched against.
 *
 * Every scope here is served from the client's subscribed rooms directory and questions slice,
 * which already hold each room and question the user is allowed to see, so searching needs no
 * server round trip and can run on every keystroke.
 *
 * TODO: comment search. Comment bodies are never part of the state snapshot (they are streamed
 * per room / per question, see routes/Comment.kt), so a COMMENTS scope needs a server endpoint
 * that scans serverState.questionComments / roomComments and replicates the per-comment
 * visibility rules from routes/Comment.kt. Being a network scope, it should stay off by default.
 */
enum class SearchScope(val id: String, val label: String) {
    QUESTION_NAME("name", "Question title"),
    QUESTION_DESCRIPTION("desc", "Question description"),
    QUESTION_TAGS("tags", "Tags"),
    ROOM("room", "Room name & description"),
    ;

    companion object {
        val default: Set<SearchScope> = entries.toSet()

        /** Parses the `in=` URL parameter. Unknown ids are ignored; if nothing is left, falls back to [default]. */
        fun parse(param: String?): Set<SearchScope> {
            if (param == null) return default
            val ids = param.split(",").map { it.trim() }.filterNot { it.isEmpty() }.toSet()
            return entries.filter { it.id in ids }.toSet().ifEmpty { default }
        }
    }
}

/** Serializes a scope set for the `in=` URL parameter, in declaration order so URLs stay stable. */
fun Set<SearchScope>.toParam(): String =
    SearchScope.entries.filter { it in this }.joinToString(",") { it.id }

// Base scores per field, plus bonuses for matching at a word start / at the very beginning.
// A question's score is the sum, over all query terms, of the best-scoring field that term hit.
private const val SCORE_NAME = 60
private const val SCORE_TAG = 45
private const val SCORE_TAG_EXACT_BONUS = 15
private const val SCORE_ROOM_NAME = 30
private const val SCORE_DESCRIPTION = 15
private const val BONUS_WORD_START = 20
private const val BONUS_PREFIX = 40

private const val SNIPPET_BEFORE = 40
private const val SNIPPET_AFTER = 120

/** A run of text to highlight, as a half-open `[start, end)` range of char indices. */
data class MatchRange(val start: Int, val end: Int)

/** An excerpt of a longer text, cut around the first match. */
data class Snippet(val text: String, val truncatedStart: Boolean, val truncatedEnd: Boolean)

data class QuestionHit(
    val question: Question,
    val room: Room,
    val score: Int,
    /** Set when the description was searched and contains a match. */
    val descriptionSnippet: Snippet?,
    val matchedTags: List<String>,
)

data class RoomHit(
    val room: Room,
    val score: Int,
    val descriptionSnippet: Snippet?,
)

data class SearchResults(
    val terms: List<String>,
    val questions: List<QuestionHit>,
    val rooms: List<RoomHit>,
) {
    val isEmpty get() = questions.isEmpty() && rooms.isEmpty()
    val total get() = questions.size + rooms.size
}

/** Splits a raw query into terms. All terms must match for a result to be included. */
fun searchTerms(query: String): List<String> =
    query.trim().split(' ', '\t', '\n').filterNot { it.isEmpty() }

/** Scores one term against one field, or 0 if the field does not contain it. */
private fun String.fieldScore(term: String, base: Int): Int {
    val idx = indexOf(term, ignoreCase = true)
    if (idx < 0) return 0
    return base + when {
        idx == 0 -> BONUS_PREFIX
        !this[idx - 1].isLetterOrDigit() -> BONUS_WORD_START
        else -> 0
    }
}

private fun questionTermScore(question: Question, term: String, scope: Set<SearchScope>): Int {
    var best = 0
    if (SearchScope.QUESTION_NAME in scope)
        best = maxOf(best, question.name.fieldScore(term, SCORE_NAME))
    if (SearchScope.QUESTION_TAGS in scope)
        question.tags.forEach { tag ->
            val score = tag.fieldScore(term, SCORE_TAG)
            if (score > 0)
                best = maxOf(best, score + if (tag.equals(term, ignoreCase = true)) SCORE_TAG_EXACT_BONUS else 0)
        }
    if (SearchScope.QUESTION_DESCRIPTION in scope)
        best = maxOf(best, question.description.fieldScore(term, SCORE_DESCRIPTION))
    return best
}

private fun roomTermScore(room: Room, term: String): Int =
    maxOf(room.name.fieldScore(term, SCORE_ROOM_NAME), room.description.fieldScore(term, SCORE_DESCRIPTION))

/**
 * Sums per-term scores, returning 0 unless *every* term matched (AND semantics), so that adding
 * words to a query always narrows the result set.
 */
private inline fun totalScore(terms: List<String>, termScore: (String) -> Int): Int {
    var total = 0
    for (term in terms) {
        val score = termScore(term)
        if (score == 0) return 0
        total += score
    }
    return total
}

/** All ranges in [text] matched by any of [terms], sorted and with overlaps merged. */
fun matchRanges(text: String, terms: List<String>): List<MatchRange> {
    val raw = mutableListOf<MatchRange>()
    terms.forEach { term ->
        if (term.isEmpty()) return@forEach
        var idx = text.indexOf(term, 0, ignoreCase = true)
        while (idx >= 0) {
            raw.add(MatchRange(idx, idx + term.length))
            idx = text.indexOf(term, idx + term.length, ignoreCase = true)
        }
    }
    raw.sortBy { it.start }
    val merged = mutableListOf<MatchRange>()
    raw.forEach { range ->
        val last = merged.lastOrNull()
        if (last != null && range.start <= last.end) merged[merged.size - 1] = MatchRange(last.start, maxOf(last.end, range.end))
        else merged.add(range)
    }
    return merged
}

/** Cuts an excerpt around the first match, snapped to word boundaries. Null if nothing matched. */
fun makeSnippet(text: String, terms: List<String>): Snippet? {
    if (text.isEmpty()) return null
    val first = terms.mapNotNull { term -> text.indexOf(term, ignoreCase = true).takeIf { it >= 0 } }.minOrNull()
        ?: return null

    var start = (first - SNIPPET_BEFORE).coerceAtLeast(0)
    var end = (first + SNIPPET_AFTER).coerceAtMost(text.length)
    while (start > 0 && text[start - 1].isLetterOrDigit()) start--
    while (end < text.length && text[end].isLetterOrDigit()) end++

    val excerpt = text.substring(start, end)
    val trimmed = excerpt.trim()
    if (trimmed.isEmpty()) return null
    return Snippet(trimmed, truncatedStart = start > 0, truncatedEnd = end < text.length)
}

/**
 * Searches everything the client knows about — the subscribed rooms directory and questions
 * slice, which already hold each room and question the user is allowed to see. Rooms are
 * matched only into their own result section: a query hitting a room name deliberately does
 * *not* pull in all of that room's questions, which would swamp the question results.
 */
fun searchWorkspace(
    rooms: Map<String, Room>,
    questions: Map<String, Question>,
    query: String,
    scope: Set<SearchScope>,
): SearchResults {
    val terms = searchTerms(query)
    if (terms.isEmpty()) return SearchResults(terms, emptyList(), emptyList())

    val questionScopeActive = scope.any { it != SearchScope.ROOM }
    val questionHits = mutableListOf<QuestionHit>()
    if (questionScopeActive) {
        val seen = mutableSetOf<String>()
        rooms.values.forEach { room ->
            room.questions.mapNotNull { questions[it.id] }.forEach { question ->
                // A question normally lives in exactly one room; guard anyway so extensions that
                // aggregate questions across rooms cannot produce duplicate rows.
                if (!seen.add(question.id)) return@forEach
                val score = totalScore(terms) { questionTermScore(question, it, scope) }
                if (score == 0) return@forEach
                questionHits.add(
                    QuestionHit(
                        question = question,
                        room = room,
                        score = score,
                        descriptionSnippet = if (SearchScope.QUESTION_DESCRIPTION in scope)
                            makeSnippet(question.description, terms) else null,
                        matchedTags = if (SearchScope.QUESTION_TAGS in scope)
                            question.tags.filter { tag -> terms.any { tag.contains(it, ignoreCase = true) } }
                        else emptyList(),
                    )
                )
            }
        }
    }

    val roomHits = mutableListOf<RoomHit>()
    if (SearchScope.ROOM in scope) {
        rooms.values.forEach { room ->
            val score = totalScore(terms) { roomTermScore(room, it) }
            if (score == 0) return@forEach
            roomHits.add(RoomHit(room, score, makeSnippet(room.description, terms)))
        }
    }

    return SearchResults(
        terms = terms,
        // Best match first; open questions outrank closed/resolved ones at equal relevance.
        questions = questionHits.sortedWith(
            compareByDescending<QuestionHit> { it.score }
                .thenByDescending { it.question.open }
                .thenBy { it.question.name.lowercase() }
        ),
        rooms = roomHits.sortedWith(
            compareByDescending<RoomHit> { it.score }.thenBy { it.room.name.lowercase() }
        ),
    )
}
