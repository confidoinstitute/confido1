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
import rooms.*
import tools.confido.application.sessions.transientUserData
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

        val myMembership = RoomMembership(user.ref, Owner, null)
        val room = serverState.roomManager.insertEntity(
            Room(id = "", name = information.name, description = information.description,
            createdAt = Clock.System.now(), questions = emptyList(),
            members = listOf(myMembership), inviteLinks = emptyList())
        )

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK, room.id)
    }
    routing.postST("/rooms/{id}/edit") {
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val user = call.userSession?.user ?: return@postST badRequest("Not logged in")
        val room = roomRef.deref() ?: return@postST badRequest("Room does not exist")
        val information: BaseRoomInformation = call.receive()

        if (!room.hasPermission(user, RoomPermission.ROOM_OWNER)) return@postST badRequest("You cannot do this")
        val roomName = information.name.ifEmpty {room.name}

        serverState.roomManager.modifyEntity(roomRef) {
            it.copy(name = roomName, description = information.description)
        }

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    routing.postST("/rooms/{id}/members/add") {
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val user = call.userSession?.user ?: return@postST badRequest("Not logged in")
        val room = roomRef.deref() ?: return@postST badRequest("Room does not exist")
        if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)) return@postST badRequest("Cannot manage members")

        val c: AddMember = call.receive()
        if (!canChangeRole(room.userRole(user), c.role)) return@postST badRequest("This role cannot be changed")

        serverState.roomManager.modifyEntity(roomRef) {
            var existing = false
            val members = it.members.map {membership ->
                if (membership.user eqid c.user) {
                    existing = true
                    membership.copy(role = c.role)
                } else membership
            }
            it.copy(members = members + if(existing) emptyList() else listOf(RoomMembership(c.user, c.role, null)))
        }

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    routing.deleteST("/rooms/{rID}/members/{id}") {
        val roomRef = Ref<Room>(call.parameters["rID"] ?: "")
        val id = call.parameters["id"] ?: ""
        val user = call.userSession?.user ?: return@deleteST badRequest("Not logged in")
        val room = roomRef.deref() ?: return@deleteST badRequest("Room does not exist")
        if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)) return@deleteST badRequest("Cannot manage members")
        val membership = room.members.find { it.user eqid id } ?: return@deleteST badRequest("No such member")
        if (!canChangeRole(room.userRole(user), membership.role)) return@deleteST badRequest("You cannot do this")

        serverState.roomManager.modifyEntity(roomRef) {
            it.copy(members = it.members.filterNot { m -> m.user eqid id })
        }

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
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