package tools.confido.application.routes

import io.ktor.http.*
import tools.confido.question.Question
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import org.simplejavamail.MailException
import payloads.requests.*
import payloads.responses.*
import rooms.*
import tools.confido.application.*
import tools.confido.application.actions.makeCommentInfo
import tools.confido.application.sessions.*
import tools.confido.question.RoomComment
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.utils.randomString
import tools.confido.utils.unixNow
import users.LoginLink
import users.User
import users.UserType
import kotlin.time.Duration.Companion.days

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
    postST("/rooms/{rID}/edit") {
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
    postST("/rooms/{rID}/members/add") {
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
    deleteST("/rooms/{rID}/members/{cID}") {
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
    postST("/rooms/{rID}/questions/reorder") {
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
    getWS("/state/rooms/{rID}/group_pred") {
        withRoom {
            val canViewPred = room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)

            val groupPreds = room.questions.associateWith { ref ->
                if (canViewPred || ref.deref()?.groupPredVisible == true) serverState.groupPred[ref] else null
            }
            groupPreds
        }
    }
}

fun roomCommentsRoutes(routing: Routing) = routing.apply {
    getWS("/state/rooms/{rID}/comments") {
        withRoom {
            assertPermission( RoomPermission.VIEW_ROOM_COMMENTS, "You cannot view the discussion.")

            val commentInfo = makeCommentInfo(user, room)
            commentInfo
        }
    }

    postST("/rooms/{rID}/comments/add") {
        withRoom {
            assertPermission(RoomPermission.POST_ROOM_COMMENT, "You cannot add comments to this room.")

            val createdComment: CreateComment = call.receive()
            if (createdComment.content.isEmpty()) badRequest("No comment content.")

            val comment = RoomComment(id = "", room = room.ref, user = user.ref, timestamp = unixNow(),
                content = createdComment.content, isAnnotation = false)
            serverState.roomCommentManager.insertEntity(comment)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    deleteST("/rooms/{rID}/comments/{cID}") {
        withRoom {
            serverState.withMutationLock {
                val id = call.parameters["cID"]
                val comment = serverState.roomComments[room.ref]?.get(id) ?: notFound("No such comment.")

                if (!room.hasPermission(
                        user,
                        RoomPermission.MANAGE_COMMENTS
                    ) && !(comment.user eqid user)
                ) unauthorized("You cannot delete this comment.")

                serverState.roomCommentManager.deleteEntity(comment, true)
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("/rooms/{rID}/comments/{cID}/like") {
        withRoom {
            val id = call.parameters["cID"] ?: ""
            val state = call.receive<Boolean>()

            assertPermission(RoomPermission.VIEW_ROOM_COMMENTS, "You cannot like this comment.")
            val comment = serverState.roomComments[room.ref]?.get(id) ?: notFound("No such comment.")

            serverState.commentLikeManager.setLike(comment.ref, user.ref, state);
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}
