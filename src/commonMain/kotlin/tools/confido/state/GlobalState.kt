package tools.confido.state

import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.question.*
import tools.confido.refs.*
import users.User


// The question collection topics ("questions", "room/<id>/questions") carry plain
// censored-entity maps keyed by question id, like the other root slices. Counts
// travel on sibling topics ("question/<id>/counts", "room/<id>/questions/counts"):
// count churn (every prediction and comment) and entity churn (occasional edits)
// have wake frequencies orders apart, so bundling them made every prediction
// re-send whole entity collections. (The former "/state" monolith is fully
// decomposed: rooms → the rooms directory topic (+ the per-room detail topic),
// per-user fields → "self", users → "users", questions → the slices above. The
// client has no global facade — surfaces subscribe the slices they display.)

// The "question/<id>/counts" topic payload: one question's per-question counts —
// bare-ref surfaces (presenter tiles) subscribe it for the live "N people
// answered" line; "room/<id>/questions/counts" ships a map of these for
// collection-shaped reads (management table columns).
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