package tools.confido.state

import rooms.Room
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.utils.PathParam
import tools.confido.utils.path

/**
 * The topic tree of the multiplexed state websocket (PERSISTENCE_SYNC_REDESIGN.md
 * §4.2), as typed [tools.confido.utils.PathTemplate]s: the client *invokes* a template
 * with entity refs to subscribe, the server *registers* the same template and receives
 * the decoded refs as typed handler arguments — construction and matching cannot
 * drift, and parameter names never appear as strings outside this file.
 *
 * Scheme: root slices are bare plurals plus `self`; entity-scoped topics use a
 * singular instance prefix; extension topics append `ext/<extensionId>/<name>` to
 * their owning scope (declared in each extension's common source, derived from the
 * prefixes here). Topic names are a namespace of their own — the websocket *URL*
 * stays `/state`.
 */
val roomRefParam = PathParam<Ref<Room>>("room", { it.id }, { s -> s.ifEmpty { null }?.let { Ref(it) } })
val questionRefParam = PathParam<Ref<Question>>("question", { it.id }, { s -> s.ifEmpty { null }?.let { Ref(it) } })
val idParam = PathParam<String>("id", { it }, { it.ifEmpty { null } })

object Topics {
    val questions = path("questions")
    val users = path("users")
    val rooms = path("rooms")
    val self = path("self")
    val presenterTrack = path("presenter/track")

    /** The per-room detail topic; also the prefix of all room-scoped topics. */
    val room = path("room") / roomRefParam
    val roomComments = room / "comments"
    val roomGroupPred = room / "group_pred"
    val presentation = room / "presentation"
    val presentationFollow = room / "presentation/follow"
    val inviteShortlink = room / "invites" / idParam / "shortlink"

    /** Prefix of all question-scoped topics. */
    val question = path("question") / questionRefParam
    val questionComments = question / "comments"
    val groupPred = question / "group_pred"
    val histogram = question / "histogram"
}
