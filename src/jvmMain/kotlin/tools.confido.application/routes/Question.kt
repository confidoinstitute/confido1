package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import payloads.requests.*
import payloads.responses.*
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.actions.makeCommentInfo
import tools.confido.application.sessions.*
import tools.confido.distributions.*
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.utils.unixNow
import users.User
import kotlin.math.min

data class QuestionContext(val inUser: User?, val question: Question) {
    val user: User by lazy { inUser ?: unauthorized("Not logged in.") }
    val room: Room by lazy { serverState.questionRoom[question.ref]?.deref() ?: notFound("No room???") }
    val ref = question.ref

    fun assertPermission(permission: RoomPermission, message: String) {
        if (!room.hasPermission(user, permission)) unauthorized(message)
    }
}

suspend fun <T> RouteBody.withQuestion(body: suspend QuestionContext.() -> T): T {
    val user = call.userSession?.user
    val qRef = Ref<Question>(call.parameters["qID"] ?: "")
    val question = qRef.deref() ?: notFound("No such question.")
    return body(QuestionContext(user, question))
}

fun questionRoutes(routing: Routing) = routing.apply {
    // Add a new question
    postST("/rooms/{rID}/questions/add") {
        withRoom {
            assertPermission(RoomPermission.ADD_QUESTION, "You cannot add questions.")

            val q: Question = call.receive()
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
    // Edit a question
    postST("/questions/{qID}/edit") {
        withQuestion {
            val editQuestion: EditQuestion = call.receive()
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "You cannot edit this question.")

            when (editQuestion) {
                is EditQuestionFlag -> {
                    serverState.questionManager.modifyEntity(ref) {
                        when (editQuestion.fieldType) {
                            EditQuestionFieldType.VISIBLE ->
                                it.copy(visible = editQuestion.value, open = it.open && editQuestion.value)
                            EditQuestionFieldType.OPEN ->
                                it.copy(open = editQuestion.value)
                            EditQuestionFieldType.GROUP_PRED_VISIBLE ->
                                it.copy(groupPredVisible = editQuestion.value)
                            EditQuestionFieldType.RESOLUTION_VISIBLE ->
                                it.copy(resolutionVisible = it.resolution != null && editQuestion.value)
                        }
                    }
                }
                is EditQuestionComplete ->
                    serverState.questionManager.replaceEntity(editQuestion.question.copy(id=question.id))
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Delete question
    deleteST("/questions/{qID}") {
        withQuestion {
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
    postST("/questions/{qID}/predict") {
        withQuestion {
            val dist: ProbabilityDistribution = call.receive()

            assertPermission(RoomPermission.SUBMIT_PREDICTION, "You cannot submit a prediction.")
            if (question.answerSpace != dist.space) badRequest("The answer space is not compatible.")

            val pred = Prediction(ts=unixNow(), dist = dist, question = question.ref, user = user.ref)
            serverState.addPrediction(pred)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    // Get question updates
    getST("/questions/{qID}/updates") {
        val updates = withQuestion {
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
    getWS("/state/questions/{qID}/group_pred") {
        withQuestion {
            if (!(room.hasPermission(user, RoomPermission.VIEW_QUESTIONS) && question.groupPredVisible)
                && !room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
            )
                unauthorized("You cannot view this group prediction.")

            serverState.groupPred[question.ref]?.let {
                it
            } ?: notFound("There is no group prediction.")
        }
    }
}

fun questionCommentsRoutes(routing: Routing) = routing.apply {
    // View comments
    getWS("/state/question/{qID}/comments") {
        withQuestion {
            assertPermission(RoomPermission.VIEW_QUESTION_COMMENTS, "You cannot view the discussion for this question.")

            val commentInfo = makeCommentInfo(user, question)
            commentInfo
        }
    }

    postST("/questions/{qID}/comments/add") {
        withQuestion {
            assertPermission(RoomPermission.POST_QUESTION_COMMENT, "You cannot post a comment to this question.")

            val createdComment: CreateComment = call.receive()
            if (createdComment.content.isEmpty()) badRequest("No comment content.")

            val prediction = if (createdComment.attachPrediction) {
                serverState.userPred[question.ref]?.get(user.ref)
            } else { null }

            val comment = QuestionComment(question = question.ref, user = user.ref, timestamp = unixNow(),
                content = createdComment.content, prediction = prediction)
            serverState.questionCommentManager.insertEntity(comment)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    deleteST("/questions/{qID}/comments/{cID}") {
        withQuestion {
            serverState.withMutationLock {
                val id = call.parameters["cID"]
                val comment = serverState.questionComments[question.ref]?.get(id)
                    ?: notFound("No such comment.")

                if (!room.hasPermission(
                        user,
                        RoomPermission.MANAGE_COMMENTS
                    ) && !(comment.user eqid user)
                ) unauthorized("No rights.")

                serverState.questionCommentManager.deleteEntity(comment, true)
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("/questions/{qID}/comments/{cID}/like") {
        withQuestion {
            val id = call.parameters["cID"] ?: ""
            val state = call.receive<Boolean>()

            assertPermission(RoomPermission.VIEW_QUESTION_COMMENTS, "You cannot like this comment.")
            val comment = serverState.questionComments[question.ref]?.get(id) ?: notFound("No such comment.")

            serverState.commentLikeManager.setLike(comment.ref, user.ref, state)
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}