package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onFailure
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import org.simplejavamail.MailException
import payloads.requests.*
import payloads.responses.*
import rooms.*
import tools.confido.application.*
import tools.confido.application.sessions.*
import tools.confido.refs.*
import tools.confido.serialization.confidoJSON
import tools.confido.state.*
import tools.confido.utils.randomString
import users.LoginLink
import users.User
import users.UserType
import kotlin.time.Duration.Companion.days

val roomUrl = Room.urlPrefix("{rID}")

data class RoomContext(val inUser: User?, val room: Room) {
    val user: User by lazy { inUser ?: unauthorized("Not logged in.") }
    val ref = room.ref

    fun assertPermission(permission: RoomPermission, message: String) {
        if (!room.hasPermission(user, permission)) unauthorized(message)
    }
}

suspend fun <T> RouteBody.withRoom(body: suspend RoomContext.() -> T): T {
    val user = call.userSession?.user
    val rRef = Ref<Room>(call.parameters["rID"] ?: "")
    val room = rRef.deref() ?: notFound("No such room.")
    return body(RoomContext(user, room))
}

fun roomRoutes(routing: Routing) = routing.apply {
    // Create a room
    postST("/rooms/add") {
        val room = withUser {
            if (!user.type.isProper()) unauthorized("Guests cannot do this.")
            val information: BaseRoomInformation = call.receive()

            val myMembership = RoomMembership(user.ref, Owner, null)
            serverState.roomManager.insertEntity(
                Room(
                    id = "", name = information.name, description = information.description,
                    createdAt = Clock.System.now(), questions = emptyList(),
                    members = listOf(myMembership), inviteLinks = emptyList()
                )
            )
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK, room.id)
    }
    // Edit a room's details
    postST("$roomUrl/edit") {
        withRoom {
            val information: BaseRoomInformation = call.receive()

            assertPermission(RoomPermission.ROOM_OWNER, "You cannot edit room details.")
            val roomName = information.name.ifEmpty {room.name}

            serverState.roomManager.modifyEntity(room.ref) {
                it.copy(name = roomName, description = information.description)
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Add a new member, either an existing user directly or a new user by e-mail
    postST("$roomUrl/members/add") {
        withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members.")
            val member: AddedMember = call.receive()
            if (!(user.type == UserType.ADMIN || canChangeRole(
                    room.userRole(user),
                    member.role
                ))
            ) unauthorized("This role cannot be changed.")

            suspend fun addExistingMember(user: User, role: RoomRole) =
                serverState.roomManager.modifyEntity(room.ref) {
                    var existing = false
                    val members = it.members.map { membership ->
                        if (membership.user eqid user) {
                            existing = true
                            membership.copy(role = role, invitedVia = null)
                        } else membership
                    } + if (existing) emptyList() else
                        listOf((RoomMembership(user.ref, role, null)))
                    it.copy(members = members)
                }

            when (member) {
                is AddedExistingMember -> {
                    // TODO: prevent in frontend
                    val existingUser = member.user.deref() ?: badRequest("User does not exist.")
                    if (!member.role.isAvailableToGuests && !existingUser.type.isProper()) badRequest("This role cannot be used for a guest.")
                    addExistingMember(existingUser, member.role)
                }
                is AddedNewMember -> {
                    serverState.userManager.byEmail[member.email.lowercase()]?.let {
                        // In this case just add a new member directly
                        if (!member.role.isAvailableToGuests && !it.type.isProper())  badRequest("This role cannot be used for a guest")
                        addExistingMember(it, member.role)
                    } ?: run {
                        val newUser = User(
                            randomString(32),
                            UserType.GUEST,
                            member.email,
                            emailVerified = false,
                            nick = null,
                            password = null,
                            createdAt = Clock.System.now()
                        )

                        val expiration = 14.days
                        val expiresAt = Clock.System.now().plus(expiration)
                        val link = LoginLink(
                            token = generateToken(),
                            user = newUser.ref,
                            expiryTime = expiresAt,
                            url = "/room/${room.id}",
                            sentToEmail = user.email?.lowercase()
                        )
                        try {
                            call.mailer.sendRoomInviteMail(
                                member.email.lowercase(),
                                room,
                                link,
                                user.email?.lowercase()
                            )
                        } catch (e: MailException) {
                            e.printStackTrace()
                            serviceUnavailable("Could not send an invitation e-mail.")
                        }

                        serverState.withTransaction {
                            serverState.userManager.insertEntity(newUser)
                            serverState.roomManager.modifyEntity(room.ref) {
                                it.copy(members = it.members + listOf(RoomMembership(newUser.ref, member.role, null)))
                            }
                            serverState.loginLinkManager.insertEntity(link)
                        }
                    }
                }
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Remove a room's member
    deleteST("$roomUrl/members/{cID}") {
        withRoom {
            val id = call.parameters["cID"] ?: ""
            val membership = room.members.find { it.user eqid id } ?: notFound("No such member.")
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members.")

            if (!canChangeRole(room.userRole(user), membership.role)) unauthorized("You cannot give this role.")

            serverState.roomManager.modifyEntity(room.ref) {
                it.copy(members = it.members.filterNot { m -> m.user eqid id })
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}

fun roomQuestionRoutes(routing: Routing) = routing.apply {
    postST("$roomUrl/questions/reorder") {
        withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage questions.")
            val move: ReorderQuestions = call.receive()

            serverState.roomManager.modifyEntity(room.ref) {
                // Questions may have gotten removed before this request happened
                val roomQuestionSet = room.questions.toSet()
                val newOrder = move.newOrder.filter { q -> q in roomQuestionSet }

                // Questions may have been added before this request happened
                val orderSet = newOrder.toSet()
                val questionsNotInOrder = it.questions.filterNot { q -> q in orderSet }

                // Append questions that are not mentioned at the end of the new order (they are likely new)
                val newQuestions = newOrder + questionsNotInOrder
                return@modifyEntity it.copy(questions = newQuestions)
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    getWS("/state$roomUrl/group_pred") {
        withRoom {
            val canViewPred = room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)

            val groupPreds = room.questions.associateWith { ref ->
                if (canViewPred || ref.deref()?.groupPredVisible == true) serverState.groupPred[ref] else null
            }
            groupPreds
        }
    }

    webSocketST("/state$roomUrl/invites/{id}/shortlink") {
        RouteBody(call).withRoom {
            val linkId = call.parameters["id"] ?: return@withRoom
            val link = room.inviteLinks.firstOrNull { it.id == linkId } ?: return@withRoom

            var curCode: String? = null
            try {
                coroutineScope {
                    async {
                        incoming.receiveCatching().onFailure {
                            this@coroutineScope.cancel()
                        }
                    }
                    async {
                        while (true) {
                            val shortLink = ShortLink(room.ref, linkId)
                            curCode?.let { serverState.shortInviteLinks.remove(it) }
                            serverState.shortInviteLinks[shortLink.shortcode] = shortLink
                            curCode = shortLink.shortcode
                            send(Frame.Text(confidoJSON.encodeToString(WSData(shortLink.shortcode) as WSResponse<String>)))
                            delay(((ShortLink.VALIDITY - 5) * 1000).toLong())
                        }
                    }
                }
            } catch (_: CancellationException) {
                //ignore
            } finally {
                if (curCode != null) {
                    serverState.shortInviteLinks.remove(curCode)
                }
            }
        }
    }
}