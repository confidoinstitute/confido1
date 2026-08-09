package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import org.simplejavamail.MailException
import payloads.requests.*
import payloads.responses.*
import rooms.*
import tools.confido.application.*
import tools.confido.application.sessions.*
import tools.confido.question.GroupPredictionVisibility
import tools.confido.refs.*
import tools.confido.serialization.confidoJSON
import tools.confido.state.*
import tools.confido.store.*
import tools.confido.utils.randomString
import users.LoginLink
import users.User
import users.UserType
import kotlin.time.Duration.Companion.days

val roomUrl = Room.urlPrefix("{rID}")
val iconNames = iconDir?.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()

data class RoomContext(val inUser: User?, val room: Room) {
    val user: User by lazy { inUser ?: unauthorized("Not logged in.") }
    val ref = room.ref

    fun assertPermission(permission: RoomPermission, message: String = "missing room privilege ${permission.id}") {
        if (!room.hasPermission(user, permission)) unauthorized(message)
    }
}

suspend fun <T> ApplicationCall.withRoom(body: suspend RoomContext.() -> T): T {
    val user = userSession?.user
    val rRef = Ref<Room>(parameters["rID"] ?: "")
    val room = rRef.deref() ?: notFound("No such room.")
    return body(RoomContext(user, room))
}

fun roomRoutes(routing: Routing) = routing.apply {
    getST("/rooms/icons") {
        // Icon names are only loaded when the application is started.
        call.respond(HttpStatusCode.OK, iconNames)
    }
    // Create a room
    postST("/rooms/add") {
        val room = call.withUser {
            if (!user.type.isProper()) unauthorized("Guests cannot do this.")
            val information: BaseRoomInformation = call.receive()

            val myMembership = RoomMembership(user.ref, appConfig.roleById("owner") ?: error("Role 'owner' not found"), null)
            tools.confido.store.roomStore.insert(
                Room(
                    id = "", name = information.name, description = information.description,
                    createdAt = Clock.System.now(), questions = emptyList(),
                    members = listOf(myMembership), inviteLinks = emptyList(),
                    color = information.color, icon = information.icon,
                    defaultSchedule = information.defaultSchedule,
                    scoring = information.scoring,
                    commentTemplate = information.commentTemplate,
                    extensionData = information.extensionData,
                )
            )
        }

        call.respond(HttpStatusCode.OK, room.id)
    }
    // Edit a room's details
    postST("$roomUrl/edit") {
        call.withRoom {
            val information: BaseRoomInformation = call.receive()

            assertPermission(RoomPermission.ROOM_OWNER, "You cannot edit room details.")
            val roomName = information.name.ifEmpty {room.name}

            tools.confido.store.roomStore.modify(room.ref) {
                it.copy(
                    name = roomName,
                    description = information.description,
                    color = information.color,
                    icon = information.icon,
                    defaultSchedule = information.defaultSchedule,
                    scoring = information.scoring,
                    commentTemplate = information.commentTemplate,
                    extensionData = information.extensionData,
                )
            }
        }

        call.respond(HttpStatusCode.OK)
    }
    // Add a new member, either an existing user directly or a new user by e-mail
    postST("$roomUrl/members/add") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members.")
            val member: AddedMember = call.receive()
            if (!(user.type == UserType.ADMIN || canChangeRole(
                    room.userRole(user),
                    member.role
                ))
            ) unauthorized("This role cannot be changed.")

            suspend fun addExistingMember(user: User, role: RoomRole) =
                tools.confido.store.roomStore.modify(room.ref) {
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
                    if (!member.role.availableToGuests && !existingUser.type.isProper()) badRequest("This role cannot be used for a guest.")
                    addExistingMember(existingUser, member.role)
                }
                is AddedNewMember -> {
                    tools.confido.store.userStore.byEmail[member.email.lowercase()]?.let {
                        // In this case just add a new member directly
                        if (!member.role.availableToGuests && !it.type.isProper())  badRequest("This role cannot be used for a guest")
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
                            url = room.urlPrefix,
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

                        transact {
                            tools.confido.store.userStore.insert(newUser)
                            tools.confido.store.roomStore.modify(room.ref) {
                                it.copy(members = it.members + listOf(RoomMembership(newUser.ref, member.role, null)))
                            }
                            loginLinkStore.insert(link)
                        }
                    }
                }
            }
        }

        call.respond(HttpStatusCode.OK)
    }
    deleteST(roomUrl) {
        call.withRoom {
            assertPermission(RoomPermission.ROOM_OWNER, "You cannot delete this room.")
            // transact (not legacy withTransaction) so the per-question cascade hooks
            // can stage into the ambient transaction
            transact {
                room.questions.forEach { question ->
                    tools.confido.store.questionStore.delete(question)
                }
                // Room comments (and each question's comments and predictions) are deleted
                // by the cascade hooks on the stores.
                tools.confido.store.roomStore.delete(room.ref)
            }
        }

        call.respond(HttpStatusCode.OK)
    }
    // Remove a room's member
    deleteST("$roomUrl/members/{cID}") {
        call.withRoom {
            val id = call.parameters["cID"] ?: ""
            val membership = room.members.find { it.user eqid id } ?: notFound("No such member.")
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members.")

            if (!canChangeRole(room.userRole(user), membership.role)) unauthorized("You cannot give this role.")

            tools.confido.store.roomStore.modify(room.ref) {
                it.copy(members = it.members.filterNot { m -> m.user eqid id })
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}

fun roomQuestionRoutes(routing: Routing) = routing.apply {
    postST("$roomUrl/questions/reorder") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage questions.")
            val move: ReorderQuestions = call.receive()

            tools.confido.store.roomStore.modify(room.ref) {
                // Questions may have gotten removed before this request happened
                val roomQuestionSet = room.questions.toSet()
                val newOrder = move.newOrder.filter { q -> q in roomQuestionSet }

                // Questions may have been added before this request happened
                val orderSet = newOrder.toSet()
                val questionsNotInOrder = it.questions.filterNot { q -> q in orderSet }

                // Append questions that are not mentioned at the end of the new order (they are likely new)
                val newQuestions = newOrder + questionsNotInOrder
                return@modify it.copy(questions = newQuestions)
            }
        }

        call.respond(HttpStatusCode.OK)
    }
    // Group predictions of a room's questions (optionally filtered by ?qids=): a
    // multi-question payload, so the composition uses whole-map views rather than
    // keyed cells — output dedup keeps unrelated changes off the wire.
    viewWS("/state$roomUrl/group_pred") {
        val wantedIds = call.request.queryParameters["qids"]?.split(',')?.toSet()
        val rId = call.parameters["rID"] ?: ""
        val viewerRef = call.userSession?.userRef ?: unauthorized("Not logged in.")
        combineViews(
            roomStore.state.asView().at(rId),
            questionStore.state.asView(),
            userStore.state.asView().at(viewerRef.id),
            latestPredictions.state.asView(),
            predictionData.groupPredState.asView(),
        ) { room, questions, viewer, userPreds, groupPreds ->
            if (viewer == null) unauthorized("Not logged in.")
            if (room == null) notFound("No such room.")
            val canViewAll = room.hasPermission(viewer, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)

            room.questions.associateWith { ref ->
                val wanted = (wantedIds?.contains(ref.id) ?: true)
                val canView = canViewGroupPred(questions[ref.id], userPreds[ref]?.get(viewerRef))

                if ((canViewAll || canView) && wanted) groupPreds[ref] else null
            }
        }
    }


    // Flow topic: rotates a short invite code for the subscription's lifetime.
    flowWS<String>("/state$roomUrl/invites/{id}/shortlink") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members.")
            val linkId = call.parameters["id"] ?: return@withRoom emptyFlow()
            room.inviteLinks.firstOrNull { it.id == linkId } ?: return@withRoom emptyFlow()

            flow {
                var curCode: String? = null
                try {
                    while (true) {
                        val shortLink = ShortLink(room.ref, linkId)
                        curCode?.let { serverState.shortInviteLinks.remove(it) }
                        serverState.shortInviteLinks[shortLink.shortcode] = shortLink
                        curCode = shortLink.shortcode
                        emit(shortLink.shortcode)
                        delay(((ShortLink.VALIDITY - 5) * 1000).toLong())
                    }
                } finally {
                    if (curCode != null) {
                        serverState.shortInviteLinks.remove(curCode)
                    }
                }
            }
        }
    }
}
