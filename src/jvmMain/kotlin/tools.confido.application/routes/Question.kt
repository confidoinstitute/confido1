package tools.confido.application.routes

import tools.confido.question.BinaryHistogramBinner
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.litote.kmongo.eq
import tools.confido.payloads.requests.*
import tools.confido.payloads.responses.*
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.api.Api
import tools.confido.application.ClassCensor
import tools.confido.application.PermClass
import tools.confido.application.UserPresentationContext
import tools.confido.application.getUserPresentation
import tools.confido.application.questionTopicScope
import tools.confido.application.sessions.*
import tools.confido.distributions.*
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.serialization.confidoJSON
import tools.confido.spaces.BinarySpace
import tools.confido.state.*
import tools.confido.store.*
import tools.confido.utils.unixNow
import tools.confido.users.User
import kotlin.math.min
import tools.confido.state.Topics

val questionUrl = Question.urlPrefix("{qID}")

data class QuestionContext(val inUser: User?, val question: Question) {
    val user: User by lazy { inUser ?: unauthorized("Not logged in.") }
    val room: Room by lazy { serverState.questionRoom[question.ref]?.deref() ?: notFound("No room???") }
    val ref = question.ref

    fun assertPermission(permission: RoomPermission, message: String) {
        if (!room.hasPermission(user, permission)) forbidden(message)
    }

    /** A question the viewer cannot see reads as nonexistent — the write-side twin of
     *  the topic gates' visibility rule (censorQuestion). Call first in any route that
     *  acts on the question on behalf of a non-managing viewer, before permission
     *  asserts, so a hidden question 404s instead of revealing its existence. */
    fun assertVisible() {
        if (!question.visible && !room.hasPermission(user, RoomPermission.VIEW_HIDDEN_QUESTIONS))
            notFound("No such question.")
    }

    fun assertGroupPredictionAccess(message: String) {
        val canViewAll = room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
        val lastPrediction = serverState.userPred[ref]?.get(user.ref)
        if (!(canViewAll || canViewGroupPred(ref.deref(), lastPrediction))) unauthorized(message)
    }
}

/**
 * The per-question part of the group-prediction visibility gate, as a pure function of
 * values (View transforms pass in the question and the viewer's own prediction they
 * combined over). Callers OR this with the room-level VIEW_ALL_GROUP_PREDICTIONS check.
 */
fun canViewGroupPred(question: Question?, myPred: Prediction?): Boolean =
    when (question?.groupPredictionVisibility) {
        GroupPredictionVisibility.EVERYONE -> true
        GroupPredictionVisibility.ANSWERED -> myPred != null
        GroupPredictionVisibility.MODERATOR_ONLY -> false
        null -> false
    }

/** Sharing key for the group-prediction topic (§4.3 extended keys): the gate
 *  *outcome*. The payload is observer-independent past the gate (one shared
 *  aggregate), so the key space is these few values per question — every viewer
 *  who may see the aggregate shares one entry regardless of permission class. */
enum class GroupPredAccess { VISIBLE, HIDDEN, FORBIDDEN, NOT_FOUND }

suspend fun <T> ApplicationCall.withQuestion(body: suspend QuestionContext.() -> T): T =
    withQuestion(Ref(parameters["qID"] ?: ""), body)

/** Topic-handler variant: the question ref comes as a typed path-template argument. */
suspend fun <T> ApplicationCall.withQuestion(qRef: Ref<Question>, body: suspend QuestionContext.() -> T): T {
    val user = userSession?.user
    val question = qRef.deref() ?: notFound("No such question.")
    return body(QuestionContext(user, question))
}

fun questionRoutes(routing: Routing) = routing.apply {
    // Add a new question
    apiST(Api.questionAdd) { rRef, newQuestion ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.ADD_QUESTION, "You cannot add questions.")

            var q = newQuestion
            // Add the initial state to the history if it has not been added already.
            if (q.stateHistory.isEmpty()) {
                q = q.copy(stateHistory = listOf(QuestionStateChange(q.state, Clock.System.now(), user.ref)))
            }
            q = q.copy(author = user.ref)

            tools.confido.store.transact {
                val question = tools.confido.store.questionStore.insert(q)
                tools.confido.store.roomStore.modify(room.ref) {
                    it.copy(questions = it.questions + listOf(question.ref))
                }
            }
        }
    }

    suspend fun doEditQuestion(question: Question, edit: EditQuestion, user: User) {
        val ref = question.ref
        when (edit) {
            is EditQuestionFlag -> {
                tools.confido.store.questionStore.modify(ref) {
                    when (edit.fieldType) {
                        EditQuestionFieldType.VISIBLE ->
                            it.copy(visible = edit.value)
                        EditQuestionFieldType.OPEN ->
                            it.copy(open = edit.value)
                        EditQuestionFieldType.GROUP_PRED_VISIBLE ->
                            it.copy(groupPredVisible = edit.value)
                        EditQuestionFieldType.RESOLUTION_VISIBLE ->
                            it.copy(resolutionVisible = it.resolution != null && edit.value)
                    }
                }
            }
            is EditQuestionComplete -> {
                tools.confido.store.transact {
                    tools.confido.store.questionStore.modify(ref) { orig->
                        if (question.numPredictions > 0 && question.answerSpace != edit.question.answerSpace) {
                            badRequest("Cannot change answer space of a question with predictions.")
                        }

                        // If withState wasn't used, this will fill in the history automatically.
                        val newHistory = if (orig.state != edit.question.state && orig.stateHistory.lastOrNull()?.newState != edit.question.state) {
                            orig.stateHistory + QuestionStateChange(edit.question.state, Clock.System.now(), user.ref)
                        } else {
                            orig.stateHistory
                        }
                        edit.question.copy(id = question.id, stateHistory = newHistory, author = orig.author)
                    }
                }
            }
            is EditQuestionState -> {
                tools.confido.store.transact {
                    tools.confido.store.questionStore.modify(ref) { orig->
                        // If withState wasn't used, this will fill in the history automatically.
                        val newHistory = if (orig.state != edit.newState && orig.stateHistory.lastOrNull()?.newState != edit.newState) {
                            orig.stateHistory + QuestionStateChange(edit.newState, Clock.System.now(), user.ref)
                        } else {
                            orig.stateHistory
                        }
                        orig.withState(edit.newState).copy(id = question.id, stateHistory = newHistory)
                    }
                }
            }
            is EditQuestionResolution -> {
                if (question.answerSpace != edit.resolution.space) badRequest("resolution space does not match answer space")
                tools.confido.store.questionStore.modify(ref) {
                    orig -> orig.copy(resolution=edit.resolution)
                }
            }
            is EditQuestionClearAnswers -> {
                clearPredictions(question.ref)
            }
        }
    }

    // Edit a question
    apiST(Api.questionEdit) { qRef, edit ->
        call.withQuestion(qRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "You cannot edit this question.")
            doEditQuestion(question, edit, user)
        }
    }
    apiST(Api.questionMultiedit) { req ->
        call.withUser {
            val questions = req.questions.mapNotNull { it.deref() }
            val rooms = questions.map{ it.room ?: badRequest("cannot modify roomless question") }
            if (!rooms.all { it.hasPermission(user, RoomPermission.MANAGE_QUESTIONS)}) unauthorized("missing permissions")
            questions.forEach {  q->
                doEditQuestion(q, req.edit, user)
            }
        }
    }
    // Delete multiple questions at once
    apiST(Api.questionMultidelete) { req ->
        call.withUser {
            val questions = req.questions.mapNotNull { it.deref() }
            val questionRooms = questions.map { it.room ?: badRequest("cannot delete roomless question") }
            if (!questionRooms.all { it.hasPermission(user, RoomPermission.MANAGE_QUESTIONS) }) unauthorized("missing permissions")
            // transact (not legacy withTransaction) so the prediction/comment cascade
            // hooks can stage into the ambient transaction. Per-question room modifies
            // of one room compose via the transaction write set (modify sees the
            // previous modify's staged result), netting one change event per room.
            transact {
                questions.forEach { q ->
                    tools.confido.store.questionStore.delete(q.ref)
                    serverState.rooms.values.toList().forEach { room ->
                        if (room.questions.contains(q.ref)) {
                            tools.confido.store.roomStore.modify(room.id) {
                                it.copy(questions = it.questions.filter { it != q.ref })
                            }
                        }
                    }
                }
            }
        }
    }
    // Bulk state change for all questions with a given tag
    apiST(Api.questionTagSetState) { rRef, tag, newState ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Need moderator privilege to alter state.")
            tools.confido.store.transact {
                room.questions.mapNotNull { it.deref() }.filter { tag in it.tags }.forEach { q ->
                    tools.confido.store.questionStore.modify(q.id) {
                        val newHistory = it.stateHistory + QuestionStateChange(newState, Clock.System.now(), user.ref)
                        it.withState(newState).copy(stateHistory = newHistory)
                    }
                }
            }
        }
    }

    apiST(Api.questionSetState) { qRef, newState ->
        call.withQuestion(qRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "You cannot edit this question.")
            tools.confido.store.questionStore.modify(ref) {
                val newHistory = question.stateHistory + QuestionStateChange(newState, Clock.System.now(), user.ref)
                it.withState(newState).copy(stateHistory = newHistory)
            }
        }
    }
    // Delete question
    apiST(Api.questionDelete) { qRef, _ ->
        call.withQuestion(qRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "You cannot delete this question.")

            transact {
                tools.confido.store.questionStore.delete(ref)
                serverState.rooms.values.toList().forEach { room ->
                    if (room.questions.contains(ref)) {
                        tools.confido.store.roomStore.modify(room.id){
                            it.copy(questions = it.questions.filter { it != ref }.toList()) }
                    }
                }
            }
        }
    }

    // Make prediction!
    apiST(Api.questionPredict) { qRef, dist ->
        call.withQuestion(qRef) {
            assertVisible()
            assertPermission(RoomPermission.SUBMIT_PREDICTION, "You cannot submit a prediction.")
            if (!question.open) badRequest("You cannot predict to closed questions.")
            if (question.answerSpace != dist.space) badRequest("The answer space is not compatible.")

            val pred = Prediction(ts=unixNow(), dist = dist, question = question.ref, user = user.ref)
            addPrediction(pred)
        }
    }

    // Get question updates
    getST("$questionUrl/updates") {
        val updates = call.withQuestion {
            assertVisible()
            assertGroupPredictionAccess("You cannot view this group prediction.")

            val updates = groupPredictionLog.query(ref).map {
                when (val dist = it.dist) {
                    is BinaryDistribution -> DistributionUpdate(
                        it.ts,
                        null,
                        listOf(dist.yesProb)
                    )
                    is ContinuousProbabilityDistribution -> DistributionUpdate(
                        it.ts,
                        dist.mean,
                        dist.discretize(min(dist.space.bins, 50)).binProbs
                    )
                    else -> DistributionUpdate(
                        it.ts,
                        null,
                        emptyList(),
                    )
                }
            }.toList().toMutableList()
            if (updates.isNotEmpty())
            // add a fake point for the current timestamp so that the graph does not abruptly end at last update time
                updates += listOf(updates[updates.size - 1].copy(ts = unixNow()))
            updates
        }
        call.respond(HttpStatusCode.OK, Cbor.encodeToByteArray(updates))
    }

    // View group predictions: shared per gate *outcome* — the workshop hot path
    // (tens of participants watching the same question's aggregate) computes once
    // per outcome, not per subscriber or class. The whole gate lives in the
    // per-subscription key view (cheap booleans); a viewer's first prediction, a
    // visibility change, or a question deletion re-keys the live subscription.
    sharedViewWS(Topics.groupPred, key = { qRef ->
        val viewerRef = call.userSession?.userRef ?: unauthorized("Not logged in.")
        combineViews(
            sharedClassView(),
            roomStore.state.asView(),
            questionStore.state.asView().at(qRef.id),
            latestPredictions.state.asView().map { it[qRef]?.get(viewerRef) },
        ) { cls, _, question, myPred ->
            val room = serverState.questionRoom[qRef]?.deref()
            val censor = ClassCensor(cls)
            when {
                question == null || room == null -> GroupPredAccess.NOT_FOUND
                !censor.hasPermission(room, RoomPermission.VIEW_QUESTIONS) -> GroupPredAccess.FORBIDDEN
                // A question the viewer cannot see reads as nonexistent, matching
                // censorQuestion (hidden questions used to leak their aggregates here).
                !question.visible
                    && !censor.hasPermission(room, RoomPermission.VIEW_HIDDEN_QUESTIONS) -> GroupPredAccess.NOT_FOUND
                censor.hasPermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
                    || canViewGroupPred(question, myPred) -> GroupPredAccess.VISIBLE
                else -> GroupPredAccess.HIDDEN
            }
        }
    }) { access, qRef ->
        when (access) {
            GroupPredAccess.VISIBLE -> predictionData.groupPredState.asView().at(qRef)
                .map { it ?: notFound("There is no group prediction.") }
            GroupPredAccess.HIDDEN -> viewOf(Unit).map { unauthorized("You cannot view this group prediction.") }
            GroupPredAccess.FORBIDDEN -> viewOf(Unit).map { forbidden("You cannot view this group prediction.") }
            GroupPredAccess.NOT_FOUND -> viewOf(Unit).map { notFound("No such question.") }
        }
    }

    // View a histogram: same View composition as group_pred, plus the whole users map
    // (per-bin UserPresentation embedding when the viewer may see individual predictions).
    viewWS(Topics.histogram) { qRef ->
        val viewerRef = call.userSession?.userRef ?: unauthorized("Not logged in.")
        val roomView = roomStore.questionRoomState.asView().at(qRef)
            .flatMap { rRef -> rRef?.let { roomStore.state.asView().at(it.id) } ?: viewOf(null) }
        combineViews(
            questionStore.state.asView().at(qRef.id),
            roomView,
            userStore.state.asView(),
            latestPredictions.state.asView().at(qRef),
        ) { question, room, users, preds ->
            val viewer = users[viewerRef.id] ?: unauthorized("Not logged in.")
            if (question == null || room == null) notFound("No such question.")
            if (!room.hasPermission(viewer, RoomPermission.VIEW_QUESTIONS))
                forbidden("You cannot view this group prediction.")
            if (!question.visible && !room.hasPermission(viewer, RoomPermission.VIEW_HIDDEN_QUESTIONS))
                notFound("No such question.")
            val canViewAll = room.hasPermission(viewer, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
            if (!(canViewAll || canViewGroupPred(question, preds?.get(viewerRef))))
                unauthorized("You cannot view this group prediction.")

            if (question.answerSpace != BinarySpace) {
                badRequest("Histograms are only supported for binary questions.")
            }

            // TODO: configurable (from query params?)
            val binner = BinaryHistogramBinner()

            val predictions = preds?.values ?: notFound("No user predictions found.")

            // Per-bin user lists are included whenever the viewer may see individual
            // predictions; clients decide whether to actually display them.
            val showNames = room.hasPermission(viewer, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS)

            if (showNames) {
                val pairs = predictions.mapNotNull { pred ->
                    val dist = pred.dist as? BinaryDistribution ?: return@mapNotNull null
                    val predUser = pred.user?.let { users[it.id] }
                    val presentation = getUserPresentation(
                        predUser, viewer, UserPresentationContext.INDIVIDUAL_PREDICTION, room
                    )
                    dist to presentation
                }
                binner.createHistogramWithUsers(pairs)
            } else {
                binner.createHistogram(predictions.map { it.dist as BinaryDistribution })
            }
        }
    }

}
