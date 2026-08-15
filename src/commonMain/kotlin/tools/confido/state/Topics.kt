package tools.confido.state

import BinaryHistogram
import kotlinx.serialization.serializer
import payloads.responses.CommentExtraData
import payloads.responses.CommentPresentation
import payloads.responses.MembershipPayload
import payloads.responses.QuestionCommentExtraData
import rooms.Room
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.utils.PathParam
import tools.confido.utils.TopicSpec0
import tools.confido.utils.TopicSpec1
import tools.confido.utils.TopicSpec2
import tools.confido.utils.path
import users.User

/**
 * The topic tree of the multiplexed state websocket (PERSISTENCE_SYNC_REDESIGN.md
 * §4.2), as typed [tools.confido.utils.TopicSpec]s: each topic binds its path shape
 * *and* its payload serializer in one declaration. The client invokes a spec with
 * entity refs to subscribe (`useSubscription(Topics.groupPred(question.ref))` — the
 * payload type is inferred), the server registers the same spec and receives the
 * decoded refs as typed handler arguments with the handler's payload type checked
 * against the spec — path shape, parameter types and payload types cannot drift
 * between the two sides.
 *
 * Scheme: root slices are bare plurals plus `self`; entity-scoped topics use a
 * singular instance prefix ([roomPath], [questionPath]); extension topics append
 * `ext/<extensionId>/<name>` to their owning scope (declared in each extension's
 * common source, derived from the prefixes here). Topic names are a namespace of
 * their own — the websocket *URL* stays `/state`.
 */
val roomRefParam = PathParam<Ref<Room>>("room", { it.id }, { s -> s.ifEmpty { null }?.let { Ref(it) } })
val questionRefParam = PathParam<Ref<Question>>("question", { it.id }, { s -> s.ifEmpty { null }?.let { Ref(it) } })
val idParam = PathParam<String>("id", { it }, { it.ifEmpty { null } })

object Topics {
    /** Prefixes of all room-/question-scoped topics (extension topics derive from these). */
    val roomPath = path("room") / roomRefParam
    val questionPath = path("question") / questionRefParam

    val questions = TopicSpec0(path("questions"), serializer<QuestionsState>())
    val users = TopicSpec0(path("users"), serializer<Map<String, User>>())
    val rooms = TopicSpec0(path("rooms"), serializer<Map<String, Room>>())
    val self = TopicSpec0(path("self"), serializer<SelfState>())
    val presenterTrack = TopicSpec0(path("presenter/track"), serializer<Unit>())

    /** The per-room detail topic (full censored room incl. invite links; member lists
     *  travel on [roomMembers] — the detail room carries only the viewer's own membership). */
    val room = TopicSpec1(roomPath, serializer<Room>())
    /** The member list as viewer-censored presentations (§5.3); subscribed by the members
     *  tab and the add-member dialog only, so membership churn stays off other surfaces. */
    val roomMembers = TopicSpec1(roomPath / "members", serializer<List<MembershipPayload>>())
    val roomComments = TopicSpec1(roomPath / "comments", serializer<List<CommentPresentation<CommentExtraData>>>())
    val roomGroupPred = TopicSpec1(roomPath / "group_pred", serializer<Map<Ref<Question>, Prediction?>>())
    val presentation = TopicSpec1(roomPath / "presentation", serializer<PresentationWSData>())
    val presentationFollow = TopicSpec1(roomPath / "presentation/follow", serializer<String?>())
    val inviteShortlink = TopicSpec2(roomPath / "invites" / idParam / "shortlink", serializer<String>())

    /** Room-scoped questions + per-question counts (§4.2): the room page's slice.
     *  Includes extension-provided effective questions (e.g. group-room aggregation). */
    val roomQuestions = TopicSpec1(roomPath / "questions", serializer<QuestionsState>())

    /** Single censored question — the by-id access behind `useQuestion` (client
     *  endgame): presenter surfaces and describers hold bare refs with no room
     *  context, so they subscribe here. Overlap with collection topics is deliberate
     *  (dedup + atomic frames make it consistent duplication). */
    val question = TopicSpec1(questionPath, serializer<Question>())

    val questionComments = TopicSpec1(questionPath / "comments", serializer<List<CommentPresentation<QuestionCommentExtraData>>>())
    val groupPred = TopicSpec1(questionPath / "group_pred", serializer<Prediction?>())
    val histogram = TopicSpec1(questionPath / "histogram", serializer<BinaryHistogram>())
}
