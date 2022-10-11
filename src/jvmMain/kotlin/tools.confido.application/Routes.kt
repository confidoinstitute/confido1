package tools.confido.application

import tools.confido.refs.*
import tools.confido.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import payloads.requests.*
import tools.confido.application.sessions.transientUserData
import rooms.Moderator
import rooms.Room
import rooms.RoomMembership
import tools.confido.application.sessions.userSession
import tools.confido.question.Question
import tools.confido.state.*

fun editQuestion(routing: Routing) {
    routing.deleteST("/questions/{id}") {
        val id = call.parameters["id"] ?: return@deleteST call.respond(HttpStatusCode.BadRequest)
        val ref = Ref<Question>(id)
        ref.deref() ?: return@deleteST call.respond(HttpStatusCode.NotFound)
        serverState.withTransaction {
            serverState.questionManager.deleteEntity(ref);
            rooms.values.toList().forEach { room ->
                if (room.questions.contains(ref)) {
                    serverState.roomManager.modifyEntity(room.id){
                        it.copy(questions = it.questions.filter { it != ref }.toList()) }
                }
            }
        }

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    routing.postST("/rooms/add") {
        val user = call.userSession?.user ?: return@postST badRequest("Not logged in")
        if (!user.type.isProper()) return@postST badRequest("Guests cannot do this")
        val information: BaseRoomInformation = call.receive()

        val myMembership = RoomMembership(user.ref, Moderator, null)
        val room = serverState.roomManager.insertEntity(
            Room(id = "", name = information.name, description = information.description,
            createdAt = Clock.System.now(), questions = emptyList(),
            members = listOf(myMembership), inviteLinks = emptyList())
        )

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK, room.id)
    }

    routing.postST("/rooms/{id}/questions/add") {
        // TODO check permissions
        @OptIn(DelicateRefAPI::class)
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        roomRef.deref() ?: return@postST badRequest("Room does not exist")
        val q: Question = call.receive()
        serverState.withTransaction {
            val question = serverState.questionManager.insertEntity(q)
            serverState.roomManager.modifyEntity(roomRef) {
                it.copy(questions = it.questions + listOf(question.ref))
            }
        }

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    routing.postST("/questions/{id}/edit") {
        @OptIn(DelicateRefAPI::class)
        val origRef = Ref<Question>(call.parameters["id"] ?: "")
        origRef.deref() ?: return@postST call.respond(HttpStatusCode.NotFound)
        val editQuestion: EditQuestion = call.receive()

        when (editQuestion) {
            is EditQuestionField -> {
                serverState.questionManager.modifyEntity(origRef.id) {
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
                serverState.questionManager.replaceEntity(editQuestion.question.withId(origRef.id))
        }


        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}