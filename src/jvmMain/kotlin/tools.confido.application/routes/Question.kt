package tools.confido.application.routes

import BinaryHistogramBinner
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
import payloads.requests.*
import payloads.responses.*
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.sessions.*
import tools.confido.distributions.*
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.serialization.confidoJSON
import tools.confido.spaces.BinarySpace
import tools.confido.state.*
import tools.confido.utils.unixNow
import users.User
import kotlin.math.min

val questionUrl = Question.urlPrefix("{qID}")

data class QuestionContext(val inUser: User?, val question: Question) {
    val user: User by lazy { inUser ?: unauthorized("Not logged in.") }
    val room: Room by lazy { serverState.questionRoom[question.ref]?.deref() ?: notFound("No room???") }
    val ref = question.ref

    fun assertPermission(permission: RoomPermission, message: String) {
        if (!room.hasPermission(user, permission)) unauthorized(message)
    }

    fun assertGroupPredictionAccess(message: String) {
        val canViewAll = room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
        val lastPrediction = serverState.userPred[ref]?.get(user.ref)
        val canView = when (ref.deref()?.groupPredictionVisibility) {
            GroupPredictionVisibility.EVERYONE -> true
            GroupPredictionVisibility.ANSWERED -> lastPrediction != null
            GroupPredictionVisibility.MODERATOR_ONLY -> false
            null -> false
        }

        if (!(canViewAll || canView)) unauthorized(message)
    }
}

suspend fun <T> ApplicationCall.withQuestion(body: suspend QuestionContext.() -> T): T {
    val user = userSession?.user
    val qRef = Ref<Question>(parameters["qID"] ?: "")
    val question = qRef.deref() ?: notFound("No such question.")
    return body(QuestionContext(user, question))
}

fun questionRoutes(routing: Routing) = routing.apply {
    // Add a new question
    postST("$roomUrl/questions/add") {
        call.withRoom {
            assertPermission(RoomPermission.ADD_QUESTION, "You cannot add questions.")

            var q: Question = call.receive()
            // Add the initial state to the history if it has not been added already.
            if (q.stateHistory.isEmpty()) {
                q = q.copy(stateHistory = listOf(QuestionStateChange(q.state, Clock.System.now(), user.ref)))
            }
            q = q.copy(author = user.ref)

            serverState.withTransaction {
                val question = serverState.questionManager.insertEntity(q)
                serverState.roomManager.modifyEntity(room.ref) {
                    it.copy(questions = it.questions + listOf(question.ref))
                }
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    suspend fun doEditQuestion(question: Question, edit: EditQuestion, user: User) {
        val ref = question.ref
        when (edit) {
            is EditQuestionFlag -> {
                serverState.questionManager.modifyEntity(ref) {
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
                serverState.withTransaction {
                    serverState.questionManager.modifyEntity(ref) { orig->
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
                serverState.withTransaction {
                    serverState.questionManager.modifyEntity(ref) { orig->
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
                serverState.questionManager.modifyEntity(ref) {
                    orig -> orig.copy(resolution=edit.resolution)
                }
            }
            is EditQuestionClearAnswers -> {
                serverState.withTransaction {
                    serverState.userPredManager.clear(question.ref)
                    serverState.userPredHistManager.clear(question.ref)
                    serverState.groupPredHistManager.clear(question.ref)
                }
            }
        }
    }

    // Edit a question
    postST("$questionUrl/edit") {
        call.withQuestion {
            val edit: EditQuestion = call.receive()
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "You cannot edit this question.")
            doEditQuestion(question, edit, user)
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("/questions/multiedit") {
        call.withUser {
            val req = call.receive<EditQuestionMulti>()
            val questions = req.questions.mapNotNull { it.deref() }
            val rooms = questions.map{ it.room ?: badRequest("cannot modify roomless question") }
            if (!rooms.all { it.hasPermission(user, RoomPermission.MANAGE_QUESTIONS)}) unauthorized("missing permissions")
            questions.forEach {  q->
                doEditQuestion(q, req.edit, user)
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("$questionUrl/state") {
        call.withQuestion {
            val newState: QuestionState = call.receive()
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "You cannot edit this question.")
            serverState.questionManager.modifyEntity(ref) {
                val newHistory = question.stateHistory + QuestionStateChange(newState, Clock.System.now(), user.ref)
                it.withState(newState).copy(stateHistory = newHistory)
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Delete question
    deleteST("$questionUrl") {
        call.withQuestion {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "You cannot delete this question.")

            serverState.withTransaction {
                serverState.questionManager.deleteEntity(ref)
                rooms.values.toList().forEach { room ->
                    if (room.questions.contains(ref)) {
                        serverState.roomManager.modifyEntity(room.id){
                            it.copy(questions = it.questions.filter { it != ref }.toList()) }
                    }
                }
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    // Make prediction!
    postST("$questionUrl/predict") {
        println("pre")
        call.withQuestion {
            val dist: ProbabilityDistribution = confidoJSON.decodeFromString(call.receiveText()) // XXX WORKAROUND KTRO-8835

            assertPermission(RoomPermission.SUBMIT_PREDICTION, "You cannot submit a prediction.")
            if (!question.open) badRequest("You cannot predict to closed questions.")
            if (question.answerSpace != dist.space) badRequest("The answer space is not compatible.")

            val pred = Prediction(ts=unixNow(), dist = dist, question = question.ref, user = user.ref)
            serverState.addPrediction(pred)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    // Get question updates
    getST("$questionUrl/updates") {
        val updates = call.withQuestion {
            assertGroupPredictionAccess("You cannot view this group prediction.")

            val updates = serverState.groupPredHistManager.query(ref).map {
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

    // View group predictions
    getWS("/state$questionUrl/group_pred") {
        call.withQuestion {
            assertPermission(RoomPermission.VIEW_QUESTIONS, "You cannot view this group prediction.")
            assertGroupPredictionAccess("You cannot view this group prediction.")

            serverState.groupPred[question.ref]?.let {
                it
            } ?: notFound("There is no group prediction.")
        }
    }

    // View a histogram
    getWS("/state$questionUrl/histogram") {
        call.withQuestion {
            assertPermission(RoomPermission.VIEW_QUESTIONS, "You cannot view this group prediction.")
            assertGroupPredictionAccess("You cannot view this group prediction.")

            if (question.answerSpace != BinarySpace) {
                badRequest("Histograms are only supported for binary questions.")
            }

            // TODO: configurable (from query params?)
            val binner = BinaryHistogramBinner()

            val predictions = serverState.userPred[question.ref]?.values ?: notFound("No user predictions found.")

            val histogram = binner.createHistogram(predictions.map { it.dist as BinaryDistribution })
            histogram
        }
    }
}
