package tools.confido.application

import tools.confido.refs.*
import tools.confido.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tools.confido.application.sessions.transientUserData
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionComplete
import payloads.requests.EditQuestionField
import payloads.requests.EditQuestionFieldType
import rooms.Room
import tools.confido.question.Question
import tools.confido.state.serverState
import tools.confido.utils.generateId
import tools.confido.utils.randomString

fun editQuestion(routing: Routing) {
    routing.delete("/delete_question/{id}") {
        val id = call.parameters["id"] ?: ""

        ServerState.questions[id]?.let {question ->
            ServerState.questions.remove(question)
            ServerState.rooms.values.map {room ->
                room.questions.remove(question.ref)
            }
            ServerState.userPredictions.values.map {userPrediction ->
                userPrediction.remove(id)
            }
            ServerState.groupPredictions.remove(id)

            call.transientUserData?.refreshRunningWebsockets()
            call.respond(HttpStatusCode.OK)
        } ?: run {
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    routing.postST("/rooms/{id}/questions/add") {
        // TODO check permissions
        @OptIn(DelicateRefAPI::class)
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        roomRef.deref() ?: return@postST call.respond(HttpStatusCode.NotFound)
        val q: Question = call.receive()
        serverState.insertEntity(q)
    }
    routing.postST("/questions/{id}/edit") {
        @OptIn(DelicateRefAPI::class)
        val origRef = Ref<Question>(call.parameters["id"] ?: "")
        origRef.deref() ?: return@postST call.respond(HttpStatusCode.NotFound)
        val editQuestion: EditQuestion = call.receive()

        when (editQuestion) {
            is EditQuestionField -> {
                serverState.modifyEntity(origRef) {
                    when (editQuestion.fieldType) {
                        EditQuestionFieldType.VISIBLE ->
                            it.copy(visible = editQuestion.value, enabled = it.enabled && editQuestion.value)

                        EditQuestionFieldType.ENABLED ->
                            it.copy(enabled = editQuestion.value)

                        EditQuestionFieldType.PREDICTIONS_VISIBLE ->
                            it.copy(predictionsVisible = editQuestion.value)

                        EditQuestionFieldType.RESOLVED -> {
                            it.copy(resolved = editQuestion.value, enabled = it.enabled && !editQuestion.value)
                        }
                    }
                }
            }
            is EditQuestionComplete ->
                serverState.updateEntity(editQuestion.question.withId(origRef.id))
        }


        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}