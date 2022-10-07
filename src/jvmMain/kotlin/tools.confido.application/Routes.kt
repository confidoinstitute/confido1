package tools.confido.application

import tools.confido.eqid.*
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
import tools.confido.utils.randomString

fun editQuestion(routing: Routing) {
    routing.delete("/delete_question/{id}") {
        val id = call.parameters["id"] ?: ""

        ServerState.questions[id]?.let {question ->
            ServerState.questions.remove(question)
            ServerState.rooms.values.map {room ->
                room.questions.removeById(question)
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

    routing.post("/edit_question/{id}") {
        val id = call.parameters["id"] ?: ""
        val editQuestion: EditQuestion = call.receive()

        when (editQuestion) {
            is EditQuestionField -> {
                println(editQuestion)
                val question = ServerState.questions[id]
                if (question == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                when (editQuestion.fieldType) {
                    EditQuestionFieldType.VISIBLE -> {
                        question.visible = editQuestion.value
                        question.enabled = question.enabled && editQuestion.value
                    }
                    EditQuestionFieldType.ENABLED -> question.enabled = editQuestion.value
                    EditQuestionFieldType.PREDICTIONS_VISIBLE -> question.predictionsVisible =
                        editQuestion.value
                    EditQuestionFieldType.RESOLVED -> {
                        question.resolved = editQuestion.value
                        question.enabled = question.enabled && !editQuestion.value
                    }
                }
            }
            is EditQuestionComplete -> {
                val qid = editQuestion.question.id.ifEmpty { randomString(20) }
                val question = editQuestion.question.copy(id=qid)
                val room = editQuestion.room
                ServerState.rooms[room]?.let {stateRoom ->
                    stateRoom.questions.insertById(question)
                    ServerState.questions.insert(question)
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            }
        }

        // TODO store the edited question to database!
        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}