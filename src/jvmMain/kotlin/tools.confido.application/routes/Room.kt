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
import tools.confido.state.Topics

val roomUrl = Room.urlPrefix("{rID}")
val iconNames = iconDir?.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()

data class RoomContext(val inUser: User?, val room: Room) {
    val user: User by lazy { inUser ?: unauthorized("Not logged in.") }
    val ref = room.ref

    fun assertPermission(permission: RoomPermission, message: String = "missing room privilege ${permission.id}") {
        if (!room.hasPermission(user, permission)) unauthorized(message)
    }
}

suspend fun <T> ApplicationCall.withRoom(body: suspend RoomContext.() -> T): T =
    withRoom(Ref(parameters["rID"] ?: ""), body)

/** Topic-handler variant: the room ref comes as a typed path-template argument. */
suspend fun <T> ApplicationCall.withRoom(rRef: Ref<Room>, body: suspend RoomContext.() -> T): T {
    val user = userSession?.user
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
    // Group predictions of a room's questions: a multi-question payload, so the
    // composition uses whole-map views rather than keyed cells — output dedup keeps
    // unrelated changes off the wire. Shared per *visible-question set* (the payload
    // is fully determined by it, so viewers of any class with the same visibility
    // share one entry; null = viewer cannot see the room — the membership gate,
    // fixing a latent leak: the topic used to serve EVERYONE-visibility aggregates
    // to non-members too). The former ?qids= filter is gone: it predates the
    // multiplexed socket, no client ever passed it, and selective consumers
    // subscribe per-question group_pred topics.
    sharedViewWS(Topics.roomGroupPred, key = { rRef ->
        val viewerRef = call.userSession?.userRef ?: unauthorized("Not logged in.")
        combineViews(
            sharedClassView(),
            roomStore.state.asView(),
            questionStore.state.asView(),
            latestPredictions.state.asView(),
        ) { cls, rooms, questions, preds ->
            val room = rooms[rRef.id] ?: return@combineViews null
            val censor = ClassCensor(cls)
            if (!censor.hasPermission(room, RoomPermission.VIEW_QUESTIONS)) return@combineViews null
            val canViewAll = censor.hasPermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
            val canViewHidden = censor.hasPermission(room, RoomPermission.VIEW_HIDDEN_QUESTIONS)
            room.questions.mapNotNull { ref ->
                val q = questions[ref.id]
                ref.id.takeIf {
                    // Questions the viewer cannot see stay out entirely — the payload
                    // keys used to enumerate hidden/draft question ids to all members.
                    q != null && (q.visible || canViewHidden) &&
                        (canViewAll || canViewGroupPred(q, preds[ref]?.get(viewerRef)))
                }
            }.toSet()
        }
    }) { visible, rRef ->
        combineViews(
            roomStore.state.asView().at(rRef.id),
            predictionData.groupPredState.asView(),
        ) { room, groupPreds ->
            if (room == null) notFound("No such room.")
            if (visible == null) unauthorized("You cannot view this room.")
            room.questions.filter { it.id in visible }.associateWith { groupPreds[it] }
        }
    }


    // Flow topic: rotates a short invite code for the subscription's lifetime.
    flowWS(Topics.inviteShortlink) { rRef, linkId ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members.")
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
