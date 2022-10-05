package tools.confido.application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tools.confido.application.sessions.transientUserData
import tools.confido.payloads.EditQuestion
import tools.confido.payloads.EditQuestionComplete
import tools.confido.payloads.EditQuestionField
import tools.confido.payloads.EditQuestionFieldType
import tools.confido.utils.randomString

fun editQuestion(routing: Routing) {
    routing.post("/edit_question/{id}") {
        println("Called edit_question")
        val id = call.parameters["id"] ?: ""
        println(id)
        val editQuestion: EditQuestion = call.receive()
        println(editQuestion)

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
                    stateRoom.getQuestion(qid)?.let {
                        stateRoom.questions.remove(it)
                    }
                    stateRoom.questions.add(question)
                    ServerState.questions[qid] = question
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