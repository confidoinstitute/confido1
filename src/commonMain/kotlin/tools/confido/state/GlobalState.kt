package tools.confido.state

import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.question.*
import tools.confido.refs.*
import users.User


// The "/state/questions" topic payload (§4.2): all questions the viewer can see, with
// the per-question count maps riding along — they are per-question facts that only
// ever existed as separate maps because the retired SentState monolith was flat.
// (The former "/state" monolith is fully decomposed: rooms → the rooms directory
// topic (+ the per-room detail topic), per-user fields → "self", users → "users",
// questions+counts → here and the per-room "room/<id>/questions" slice. The client
// has no global facade — surfaces subscribe the slices they display.)
@Serializable
data class QuestionsState(
    val questions: Map<String, Question> = emptyMap(),
    val predictionCount: Map<Ref<Question>, Int> = emptyMap(),
    val predictorCount: Map<Ref<Question>, Int> = emptyMap(),
    val commentCount: Map<Ref<Question>, Int> = emptyMap(),
)

// The "question/<id>/counts" topic payload: one question's per-question counts.
// A sibling of "question/<id>" rather than part of it, so count churn (every
// prediction and comment) never frames the entity topic — bare-ref surfaces
// (presenter tiles) subscribe this one for the live "N people answered" line.
@Serializable
data class QuestionCounts(
    val predictions: Int = 0,
    val predictors: Int = 0,
    val comments: Int = 0,
)

// The per-user state slice sent over the "/state/self" topic
// (PERSISTENCE_SYNC_REDESIGN.md §4.2). Small and per-user by nature; keeping it out
// of the shared topics makes those purely permission-class-shaped and stops session
// churn (renewals, presenter windows, own predictions) from invalidating them.
@Serializable
data class SelfState(
    /** The viewer's own User (password scrubbed). The client resolves `Ref<User>`
     *  against this — no user other than oneself is ever client-derefable (the users
     *  map left ordinary clients; other identities arrive as UserPresentations
     *  embedded in payloads, §5.3). */
    val user: User? = null,
    // The two session fields the client actually reads — the raw UserSession is
    // deliberately NOT shipped (it is a credential-bearing server entity; shipping
    // it also required pinning expiryTime so session renewals deduped away).
    val presenterInfo: PresenterInfo? = null,
    val followingPresentation: Ref<Room>? = null,
    val myPredictions: Map<Ref<Question>, Prediction> = emptyMap(),
    val myPasswordIsSet: Boolean = false,
    val presenterWindowActive: Boolean = false,
)