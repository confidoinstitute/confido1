package tools.confido.api

import kotlinx.serialization.serializer
import tools.confido.payloads.requests.*
import tools.confido.payloads.responses.*
import tools.confido.rooms.InviteLink
import tools.confido.rooms.TimelineMark
import tools.confido.calibration.CalibrationVector
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.CommentScope
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.state.PresenterView
import tools.confido.state.idParam
import tools.confido.state.questionRefParam
import tools.confido.state.roomRefParam
import tools.confido.utils.ApiMethod.DELETE
import tools.confido.utils.ApiMethod.GET
import tools.confido.utils.ApiMethod.POST
import tools.confido.utils.ApiSpec
import tools.confido.utils.BoundApi
import tools.confido.utils.PathParam
import tools.confido.utils.path
import tools.confido.users.DeleteUserOptions
import tools.confido.users.User

val userRefParam = PathParam<Ref<User>>("user", { it.id }, { s -> s.ifEmpty { null }?.let { Ref(it) } })
val tagParam = PathParam<String>("tag", { it }, { it.ifEmpty { null } })
val tokenParam = PathParam<String>("token", { it }, { it.ifEmpty { null } })
val tsParam = PathParam<Int>("ts", { it.toString() }, { it.toIntOrNull() })

/**
 * The typed HTTP API surface, as [tools.confido.utils.ApiSpec]s — the request/response
 * counterpart of [tools.confido.state.Topics]: each endpoint binds its method, URL shape
 * and body types in one declaration shared by server registration (`apiST`) and client
 * invocation (`Client.sendData(Api.x(ref), payload)`).
 *
 * Migration is incremental: endpoints converted from string routes keep their URL shape
 * (so unconverted callers and tests see no change), but bodies go through the spec's
 * serializers on both sides — notably String payloads become JSON strings instead of
 * raw text. Comments are the first converted group; the rest of the routes still use
 * plain `getST`/`postST` with string paths.
 */
object Api {
    /** Instance prefixes of entity-scoped endpoints, matching Room/Question.urlPrefix. */
    val roomPrefix = path("rooms") / roomRefParam
    val questionPrefix = path("questions") / questionRefParam

    val questionCommentAdd = ApiSpec(POST, questionPrefix / "comments/add", serializer<CreateComment>(), serializer<Unit>())
    val questionCommentEdit = ApiSpec(POST, questionPrefix / "comments" / idParam / "edit", serializer<String>(), serializer<Unit>())
    val questionCommentDelete = ApiSpec(DELETE, questionPrefix / "comments" / idParam, serializer<Unit>(), serializer<Unit>())
    val questionCommentLike = ApiSpec(POST, questionPrefix / "comments" / idParam / "like", serializer<Boolean>(), serializer<Unit>())

    val roomCommentAdd = ApiSpec(POST, roomPrefix / "comments/add", serializer<CreateComment>(), serializer<Unit>())
    val roomCommentEdit = ApiSpec(POST, roomPrefix / "comments" / idParam / "edit", serializer<String>(), serializer<Unit>())
    val roomCommentDelete = ApiSpec(DELETE, roomPrefix / "comments" / idParam, serializer<Unit>(), serializer<Unit>())
    val roomCommentLike = ApiSpec(POST, roomPrefix / "comments" / idParam / "like", serializer<Boolean>(), serializer<Unit>())

    val questionAdd = ApiSpec(POST, roomPrefix / "questions/add", serializer<Question>(), serializer<Unit>())
    val questionEdit = ApiSpec(POST, questionPrefix / "edit", serializer<EditQuestion>(), serializer<Unit>())
    val questionSetState = ApiSpec(POST, questionPrefix / "state", serializer<QuestionState>(), serializer<Unit>())
    val questionDelete = ApiSpec(DELETE, questionPrefix, serializer<Unit>(), serializer<Unit>())
    val questionPredict = ApiSpec(POST, questionPrefix / "predict", serializer<ProbabilityDistribution>(), serializer<Unit>())
    /** The group prediction as of a historical unix timestamp (newest log entry <= ts).
     *  Backs the timeline's scrub-ghost overlay; gated like the group_pred topic. */
    val questionGroupPredAt = ApiSpec(GET, questionPrefix / "group_pred_at" / tsParam, serializer<Unit>(), serializer<Prediction?>())
    /** Every predictor's prediction at two historical timestamps (null = latest).
     *  Backs the compare view's individual tabs; needs VIEW_INDIVIDUAL_PREDICTIONS. */
    val questionUserPredCompare = ApiSpec(POST, questionPrefix / "user_preds_at", serializer<UserPredCompareRequest>(), serializer<List<UserPredCompareRow>>())

    // Timeline markers (id and owner in the sent mark are server-assigned).
    val questionTimelineMarkAdd = ApiSpec(POST, questionPrefix / "timeline_marks/add", serializer<TimelineMark>(), serializer<Unit>())
    val questionTimelineMarkDelete = ApiSpec(DELETE, questionPrefix / "timeline_marks" / idParam, serializer<Unit>(), serializer<Unit>())
    val roomTimelineMarkAdd = ApiSpec(POST, roomPrefix / "timeline_marks/add", serializer<TimelineMark>(), serializer<Unit>())
    val roomTimelineMarkDelete = ApiSpec(DELETE, roomPrefix / "timeline_marks" / idParam, serializer<Unit>(), serializer<Unit>())
    val questionMultiedit = ApiSpec(POST, path("questions/multiedit"), serializer<EditQuestionMulti>(), serializer<Unit>())
    val questionMultidelete = ApiSpec(POST, path("questions/multidelete"), serializer<DeleteQuestionMulti>(), serializer<Unit>())
    val questionTagSetState = ApiSpec(POST, roomPrefix / "tags" / tagParam / "set_state", serializer<QuestionState>(), serializer<Unit>())
    val questionsReorder = ApiSpec(POST, roomPrefix / "questions/reorder", serializer<ReorderQuestions>(), serializer<Unit>())

    val roomIcons = ApiSpec(GET, path("rooms/icons"), serializer<Unit>(), serializer<List<String>>())
    /** Responds with the created room's id. */
    val roomAdd = ApiSpec(POST, path("rooms/add"), serializer<BaseRoomInformation>(), serializer<String>())
    val roomEdit = ApiSpec(POST, roomPrefix / "edit", serializer<BaseRoomInformation>(), serializer<Unit>())
    val roomDelete = ApiSpec(DELETE, roomPrefix, serializer<Unit>(), serializer<Unit>())
    val roomMemberAdd = ApiSpec(POST, roomPrefix / "members/add", serializer<AddedMember>(), serializer<Unit>())
    val roomMemberDelete = ApiSpec(DELETE, roomPrefix / "members" / userRefParam, serializer<Unit>(), serializer<Unit>())

    val login = ApiSpec(POST, path("login"), serializer<PasswordLogin>(), serializer<Unit>())
    val loginEmailCreate = ApiSpec(POST, path("login_email/create"), serializer<SendMailLink>(), serializer<Unit>())
    /** Responds with the login link's post-login redirect URL. */
    val loginEmail = ApiSpec(POST, path("login_email"), serializer<EmailLogin>(), serializer<String>())
    /** Devmode/demo only. */
    val loginUsersList = ApiSpec(GET, path("login_users"), serializer<Unit>(), serializer<List<User>>())
    /** Devmode/demo only. */
    val loginUsers = ApiSpec(POST, path("login_users"), serializer<UsernameLogin>(), serializer<Unit>())
    val logout = ApiSpec(POST, path("logout"), serializer<Unit>(), serializer<Unit>())

    /** Workspace-wide search over the viewer-censored rooms and questions. */
    val search = ApiSpec(POST, path("search"), serializer<SearchRequest>(), serializer<SearchResponse>())

    val profileEmailStartVerification = ApiSpec(POST, path("profile/email/start_verification"), serializer<StartEmailVerification>(), serializer<Unit>())
    val profileEmailVerify = ApiSpec(POST, path("profile/email/verify"), serializer<TokenVerification>(), serializer<Unit>())
    val profilePassword = ApiSpec(POST, path("profile/password"), serializer<SetPassword>(), serializer<Unit>())
    val profilePasswordReset = ApiSpec(POST, path("profile/password/reset"), serializer<Unit>(), serializer<Unit>())
    val profilePasswordResetFinish = ApiSpec(POST, path("profile/password/reset/finish"), serializer<TokenVerification>(), serializer<Unit>())
    val profileNick = ApiSpec(POST, path("profile/nick"), serializer<SetNick>(), serializer<Unit>())
    /** Set/clear one personal UI preference (User.uiSettings); syncs back via the self slice. */
    val profileUiSetting = ApiSpec(POST, path("profile/ui_setting"), serializer<SetUiSetting>(), serializer<Unit>())
    val profileEdit = ApiSpec(POST, path("profile/edit"), serializer<EditProfile>(), serializer<EditProfileResult>())

    val joinCheck = ApiSpec(GET, path("join") / tokenParam / "check", serializer<Unit>(), serializer<InviteStatus>())
    /** Responds with the created invite link. */
    val inviteCreate = ApiSpec(POST, roomPrefix / "invites/create", serializer<CreateNewInvite>(), serializer<InviteLink>())
    val inviteEdit = ApiSpec(POST, roomPrefix / "invites/edit", serializer<InviteLink>(), serializer<Unit>())
    val inviteDelete = ApiSpec(DELETE, roomPrefix / "invite", serializer<DeleteInvite>(), serializer<Unit>())
    val inviteAccept = ApiSpec(POST, roomPrefix / "invite/accept", serializer<AcceptInvite>(), serializer<Unit>())
    /** Responds true if a user with the given email already existed (and was added instead of created). */
    val inviteAcceptNewUser = ApiSpec(POST, roomPrefix / "invite/accept_newuser", serializer<AcceptInviteAndCreateUser>(), serializer<Boolean>())

    val userEdit = ApiSpec(POST, path("users/edit"), serializer<User>(), serializer<Unit>())
    val userDelete = ApiSpec(DELETE, path("users") / userRefParam, serializer<DeleteUserOptions>(), serializer<Unit>())
    /** `?invite` query parameter requests an invitation e-mail (not modeled by the spec). */
    val userAdd = ApiSpec(POST, path("users/add"), serializer<User>(), serializer<Unit>())

    val myScores = ApiSpec(GET, path("scores/my"), serializer<Unit>(), serializer<MyScoresResponse>())
    val scoreboard = ApiSpec(GET, roomPrefix / "scoreboard.api", serializer<Unit>(), serializer<List<ScoreboardEntry>>())
    val myRank = ApiSpec(GET, roomPrefix / "my_rank", serializer<Unit>(), serializer<MyRankResponse>())
    val myScore = ApiSpec(GET, questionPrefix / "my_score", serializer<Unit>(), serializer<Pair<Prediction?, Double?>>())

    val calibration = ApiSpec(POST, path("calibration"), serializer<CalibrationRequest>(), serializer<CalibrationVector>())
    val calibrationDetail = ApiSpec(POST, path("calibration/detail"), serializer<CalibrationRequest>(), serializer<List<CalibrationQuestion>>())

    val presenterSet = ApiSpec(POST, path("presenter/set"), serializer<PresenterView>(), serializer<Unit>())
    val presenterStop = ApiSpec(POST, path("presenter/stop"), serializer<Unit>(), serializer<Unit>())

    /** Responds with the created presentation's id. */
    val presentationCreate = ApiSpec(POST, roomPrefix / "presentation/create", serializer<CreatePresentation>(), serializer<String>())
    val presentationEdit = ApiSpec(POST, roomPrefix / "presentation/edit", serializer<EditPresentation>(), serializer<Unit>())
    val presentationDelete = ApiSpec(DELETE, roomPrefix / "presentation", serializer<Unit>(), serializer<Unit>())
    val presentationStart = ApiSpec(POST, roomPrefix / "presentation/start", serializer<Unit>(), serializer<Unit>())
    val presentationStop = ApiSpec(POST, roomPrefix / "presentation/stop", serializer<Unit>(), serializer<Unit>())
    val presentationNavigate = ApiSpec(POST, roomPrefix / "presentation/navigate", serializer<NavigatePresentation>(), serializer<Unit>())
    val presentationSetView = ApiSpec(POST, roomPrefix / "presentation/setview", serializer<PresenterView>(), serializer<Unit>())
    /** Discard the current step's live view override and restore the scripted view. */
    val presentationRevertView = ApiSpec(POST, roomPrefix / "presentation/revertview", serializer<Unit>(), serializer<Unit>())
    val presentationReexecute = ApiSpec(POST, roomPrefix / "presentation/reexecute", serializer<Unit>(), serializer<Unit>())
    val presentationFollow = ApiSpec(POST, roomPrefix / "presentation/follow", serializer<Unit>(), serializer<Unit>())
    val presentationUnfollow = ApiSpec(POST, roomPrefix / "presentation/unfollow", serializer<Unit>(), serializer<Unit>())

    val importParseUrl = ApiSpec(POST, roomPrefix / "questions/import/parse-url", serializer<ImportUrlRequest>(), serializer<CsvParseResponse>())
    val importQuestions = ApiSpec(POST, roomPrefix / "questions/import", serializer<ImportQuestions>(), serializer<ImportResult>())

    val feedback = ApiSpec(POST, path("feedback"), serializer<String>(), serializer<Unit>())
}

// Scope-dispatched bindings: a CommentPresentation carries its typed CommentScope, so
// the client binds a comment's endpoints from refs (the question- and room-scoped spec
// pairs share wire types, so each pair dispatches to one BoundApi type).

fun CommentPresentation<*>.editApi(): BoundApi<String, Unit> = when (val s = scope) {
    is CommentScope.QuestionScope -> Api.questionCommentEdit(s.question, commentId.id)
    is CommentScope.RoomScope -> Api.roomCommentEdit(s.room, commentId.id)
}

fun CommentPresentation<*>.deleteApi(): BoundApi<Unit, Unit> = when (val s = scope) {
    is CommentScope.QuestionScope -> Api.questionCommentDelete(s.question, commentId.id)
    is CommentScope.RoomScope -> Api.roomCommentDelete(s.room, commentId.id)
}

fun CommentPresentation<*>.likeApi(): BoundApi<Boolean, Unit> = when (val s = scope) {
    is CommentScope.QuestionScope -> Api.questionCommentLike(s.question, commentId.id)
    is CommentScope.RoomScope -> Api.roomCommentLike(s.room, commentId.id)
}
