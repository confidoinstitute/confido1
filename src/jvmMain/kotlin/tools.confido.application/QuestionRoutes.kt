package tools.confido.application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import payloads.requests.*
import payloads.responses.DistributionUpdate
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.transientUserData
import tools.confido.application.sessions.userSession
import tools.confido.distributions.*
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.question.QuestionComment
import tools.confido.refs.*
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.NumericSpace
import tools.confido.state.deleteEntity
import tools.confido.state.insertEntity
import tools.confido.state.modifyEntity
import tools.confido.state.serverState
import tools.confido.utils.unixNow
import kotlin.math.min

fun questionRoutes(routing: Routing) = routing.apply {
    // Add a new question
    postST("/rooms/{id}/questions/add") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val room = roomRef.deref() ?: return@postST notFound("Room does not exist.")
        if (!room.hasPermission(user, RoomPermission.ADD_QUESTION)) return@postST unauthorized("You cannot add questions.")

        val q: Question = call.receive()
        serverState.withTransaction {
            val question = serverState.questionManager.insertEntity(q)
            serverState.roomManager.modifyEntity(roomRef) {
                it.copy(questions = it.questions + listOf(question.ref))
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Edit a question
    postST("/questions/{id}/edit") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        val origRef = Ref<Question>(call.parameters["id"] ?: "")
        val question = origRef.deref() ?: return@postST call.respond(HttpStatusCode.NotFound)
        val editQuestion: EditQuestion = call.receive()

        // Ensure that you have right to manage this question.
        if (serverState.questionRoom[origRef]?.deref()?.hasPermission(user, RoomPermission.MANAGE_QUESTIONS) != true)
            return@postST unauthorized("You cannot edit this question.")

        when (editQuestion) {
            is EditQuestionFlag -> {
                serverState.questionManager.modifyEntity(origRef.id) {
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
                serverState.questionManager.replaceEntity(editQuestion.question.copy(id=origRef.id))
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Delete question
    deleteST("/questions/{id}") {
        val user = call.userSession?.user ?: return@deleteST unauthorized("Not logged in.")
        val ref = Ref<Question>(call.parameters["id"] ?: "")
        val question = ref.deref() ?: return@deleteST call.respond(HttpStatusCode.NotFound)

        // Ensure that you have right to manage this question.
        if (serverState.questionRoom[ref]?.deref()?.hasPermission(user, RoomPermission.MANAGE_QUESTIONS) != true)
            return@deleteST unauthorized("You cannot edit this question.")

        serverState.withTransaction {
            serverState.questionManager.deleteEntity(ref);
            rooms.values.toList().forEach { room ->
                if (room.questions.contains(ref)) {
                    serverState.roomManager.modifyEntity(room.id){
                        it.copy(questions = it.questions.filter { it != ref }.toList()) }
                }
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    // Make prediction!
    postST("/questions/{id}/predict") {
        val dist: ProbabilityDistribution = call.receive()
        val ref = Ref<Question>(call.parameters["id"] ?: "")
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        val question = ref.deref() ?: return@postST notFound("No such question.")

        if (serverState.questionRoom[ref]?.deref()?.hasPermission(user, RoomPermission.SUBMIT_PREDICTION) != true)
            return@postST unauthorized("You cannot submit a prediction.")
        if (question.answerSpace != dist.space) return@postST badRequest("The answer space is not compatible.")

        val pred = Prediction(ts=unixNow(), dist = dist, question = question.ref, user = user.ref)
        serverState.addPrediction(pred)

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    // Get question updates
    getST("/questions/{id}/updates") {
        val ref = Ref<Question>(call.parameters["id"] ?: "")
        //val user = call.userSession?.user ?: return@getST unauthorized("Not logged in.")
        val question = ref.deref() ?: return@getST notFound("No such question.")

        var updates = serverState.groupPredHistManager.query(ref).map {
            val dist = it.dist
            when(dist) {
                is BinaryDistribution -> DistributionUpdate(
                    it.ts,
                    null,
                    listOf(dist.yesProb)
                )
                is ContinuousProbabilityDistribution -> DistributionUpdate(
                    it.ts,
                    dist.mean,
                    dist.discretize(min(dist.space.bins,50)).binProbs
                )
                else -> DistributionUpdate(
                    it.ts,
                    null,
                    emptyList(),
                )
            }
        }
        if (updates.isNotEmpty())
            // add a fake point for the current timestamp so that the graph does not abruptly end at last update time
            updates += listOf(updates[updates.size - 1].copy(ts=unixNow()))
        call.respond(HttpStatusCode.OK, Cbor.encodeToByteArray(updates))
    }
}

fun questionCommentsRoutes(routing: Routing) = routing.apply {
    postST("/questions/{id}/comments/add") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        val id = call.parameters["id"] ?: ""
        val question = serverState.questions[id] ?: return@postST notFound("No such question.")

        val createdComment: CreateComment = call.receive()
        if (createdComment.content.isEmpty()) return@postST badRequest("No comment content.")

        val prediction = if (createdComment.attachPrediction) {
            serverState.userPred[question.ref]?.get(user.ref)
        } else {
            null
        }

        val comment = QuestionComment(question = question.ref, user = user.ref, timestamp = unixNow(),
            content = createdComment.content, prediction = prediction)
        serverState.questionCommentManager.insertEntity(comment)

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    deleteST("/questions/{qID}/comments/{id}") {
        val qID = call.parameters["qID"] ?: ""
        val id = call.parameters["id"] ?: ""
        val user = call.userSession?.user ?: return@deleteST unauthorized("Not logged in.")

        serverState.withMutationLock {
            val question = serverState.questions[qID] ?: return@withMutationLock notFound("No such question.")
            val room = serverState.questionRoom[question.ref]?.deref() ?: return@withMutationLock notFound("No room???")
            val comment = serverState.questionComments[question.ref]?.get(id) ?: return@withMutationLock notFound("No such comment.")

            if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user)) return@withMutationLock unauthorized("No rights.")

            serverState.questionCommentManager.deleteEntity(comment, true)

            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }
    postST("/questions/{qID}/comments/{id}/like") {
        val qID = call.parameters["qID"] ?: ""
        val id = call.parameters["id"] ?: ""
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        val state = call.receive<Boolean>()

        serverState.withMutationLock {
            val question = serverState.questions[qID] ?: return@withMutationLock notFound("No such question.")
            val room = serverState.questionRoom[question.ref]?.deref() ?: return@withMutationLock notFound("No room???")
            val comment = serverState.questionComments[question.ref]?.get(id)
                ?: return@withMutationLock notFound("No such comment.")
            serverState.commentLikeManager.setLike(user.ref, state);
        }
    }
}