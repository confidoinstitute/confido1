package tools.confido.application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import org.simplejavamail.MailException
import payloads.requests.*
import rooms.*
import tools.confido.application.sessions.transientUserData
import tools.confido.application.sessions.userSession
import tools.confido.question.RoomComment
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.utils.randomString
import tools.confido.utils.unixNow
import users.LoginLink
import users.User
import users.UserType
import kotlin.time.Duration.Companion.days

fun roomRoutes(routing: Routing) = routing.apply {
    // Create a room
    postST("/rooms/add") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        if (!user.type.isProper()) return@postST unauthorized("Guests cannot do this.")
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
    // Edit a room's details
    postST("/rooms/{id}/edit") {
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        val room = roomRef.deref() ?: return@postST notFound("This room does not exist.")
        val information: BaseRoomInformation = call.receive()

        if (!room.hasPermission(user, RoomPermission.ROOM_OWNER)) return@postST unauthorized("You cannot edit room details.")
        val roomName = information.name.ifEmpty {room.name}

        serverState.roomManager.modifyEntity(roomRef) {
            it.copy(name = roomName, description = information.description)
        }

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Add a new member, either an existing user directly or a new user by e-mail
    postST("/rooms/{id}/members/add") {
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in")
        val room = roomRef.deref() ?: return@postST notFound("Room does not exist")
        if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)) return@postST unauthorized("Cannot manage members")

        val member: AddedMember = call.receive()
        if (!canChangeRole(room.userRole(user), member.role)) return@postST unauthorized("This role cannot be changed")

        suspend fun addExistingMember(user: Ref<User>, role: RoomRole) =
            serverState.roomManager.modifyEntity(roomRef) {
                var existing = false
                val members = it.members.map { membership ->
                    if (membership.user eqid user) {
                        existing = true
                        membership.copy(role = role, invitedVia = null)
                    } else membership
                } + if(existing) emptyList() else
                    listOf((RoomMembership(user, role, null)))
                it.copy(members = members)
            }

        when(member) {
            is AddedExistingMember -> {
                addExistingMember(member.user, member.role)
                call.transientUserData?.refreshRunningWebsockets()
            }
            is AddedNewMember -> {
                serverState.userManager.byEmail[member.email]?.let {
                    // In this case just add a new member directly
                    addExistingMember(it.ref, member.role)
                } ?: run {
                    val newUser = User(
                        randomString(32),
                        UserType.MEMBER,
                        member.email,
                        emailVerified = false,
                        nick = null,
                        password = null,
                        createdAt = Clock.System.now()
                    )

                    val expiration = 14.days
                    val expiresAt = Clock.System.now().plus(expiration)
                    val link = LoginLink(user = user.ref, expiryTime = expiresAt, url = "/room/${room.id}")
                    try {
                        call.mailer.sendDirectInviteMail(member.email, room, link, user.email)
                    } catch (e: MailException) {
                        return@postST call.respond(HttpStatusCode.ServiceUnavailable, "Could not send an invitation e-mail.")
                    }

                    serverState.withTransaction {
                        serverState.userManager.insertEntity(newUser)
                        serverState.roomManager.modifyEntity(room.ref) {
                            it.copy(members = it.members + listOf(RoomMembership(user.ref, member.role, null)))
                        }
                        serverState.loginLinkManager.insertEntity(link)
                    }
                }
            }
        }

        call.respond(HttpStatusCode.OK)
    }
    // Remove a room's member
    deleteST("/rooms/{rID}/members/{id}") {
        val roomRef = Ref<Room>(call.parameters["rID"] ?: "")
        val id = call.parameters["id"] ?: ""
        val user = call.userSession?.user ?: return@deleteST unauthorized("Not logged in.")
        val room = roomRef.deref() ?: return@deleteST notFound("This room does not exist.")
        val membership = room.members.find { it.user eqid id } ?: return@deleteST notFound("No such member.")
        if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)) return@deleteST unauthorized("Cannot manage members.")
        if (!canChangeRole(room.userRole(user), membership.role)) return@deleteST unauthorized("You cannot do this.")

        serverState.roomManager.modifyEntity(roomRef) {
            it.copy(members = it.members.filterNot { m -> m.user eqid id })
        }

        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}

fun roomCommentsRoutes(routing: Routing) = routing.apply {
    postST("/rooms/{id}/comments/add") {
        val id = call.parameters["id"] ?: ""
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        val room = serverState.rooms[id] ?: return@postST notFound("No such room.")

        val createdComment: CreateComment = call.receive()
        if (createdComment.content.isEmpty()) return@postST badRequest("No comment content.")

        val comment = RoomComment(id = "", room = room.ref, user = user.ref, timestamp = unixNow(),
            content = createdComment.content, isAnnotation = false)
        serverState.roomCommentManager.insertEntity(comment)
        call.transientUserData?.refreshRunningWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    deleteST("/rooms/{rID}/comments/{id}") {
        val rID = call.parameters["rID"] ?: ""
        val id = call.parameters["id"] ?: ""
        val user = call.userSession?.user ?: return@deleteST unauthorized("Not logged in.")

        serverState.withMutationLock {
            val room = serverState.rooms[rID] ?: return@withMutationLock notFound("No such room.")
            val comment = serverState.roomComments[room.ref]?.get(id) ?: return@withMutationLock notFound("No such comment.")

            if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user)) return@withMutationLock unauthorized("You cannot do this.")

            serverState.roomCommentManager.deleteEntity(comment, true)

            call.transientUserData?.refreshRunningWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }
}