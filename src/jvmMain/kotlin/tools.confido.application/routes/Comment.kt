package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import payloads.requests.CreateComment
import payloads.responses.CommentExtraData
import payloads.responses.CommentPresentation
import payloads.responses.QuestionCommentExtraData
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.UserPresentationContext
import tools.confido.application.getUserPresentation
import tools.confido.application.sessions.TransientData
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.state.insertEntity
import tools.confido.state.modifyEntity
import tools.confido.state.serverState
import tools.confido.utils.unixNow
import users.User

const val commentUrl = "/comments/{cID}"

inline fun <reified T: Entity> commentList(entity: T) = when(entity) {
    is Question -> serverState.questionComments[entity.ref]
    is Room -> serverState.roomComments[entity.ref]
    else -> error("This entity does not have comments")
}


fun<T: CommentExtraData> makeCommentPresentation(comment: Comment, extraData: T?, observer: User, room: Room) =
    CommentPresentation(
        comment.ref,
        comment.urlPrefix,
        getUserPresentation(comment.user.deref(), observer, UserPresentationContext.COMMENT_AUTHOR, room),
        comment.timestamp,
        comment.content,
        comment.modified,
        serverState.commentLikeManager.numLikes[comment.ref] ?: 0,
        comment.ref in (serverState.commentLikeManager.byUser[observer.ref] ?: emptySet()),
        extraData
    )

fun questionCommentsRoutes(routing: Routing) = routing.apply {
    // View comments
    getWS("/state$questionUrl/comments") {
        call.withQuestion {
            assertPermission(RoomPermission.VIEW_QUESTION_COMMENTS, "You cannot view the discussion for this question.")

            val canViewAll = room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
            val lastPrediction = serverState.userPred[ref]?.get(user.ref)
            val canView = when (question.commentVisibility) {
                CommentVisibility.EVERYONE -> true
                CommentVisibility.ANSWERED -> lastPrediction != null
                CommentVisibility.MODERATOR_ONLY -> false
            }

            val comments = serverState.questionComments[ref]?.values?.filter { comment ->
                comment.user == user.ref || (canViewAll || canView)
            }?.map { comment->
                makeCommentPresentation(comment, QuestionCommentExtraData(comment.prediction?.dist), user, room)
            }?:emptyList()
            comments
        }
    }

    postST("$questionUrl/comments/add") {
        call.withQuestion {
            assertPermission(RoomPermission.POST_QUESTION_COMMENT, "You cannot post a comment to this question.")

            if (!question.allowComments) unauthorized("Commenting is not allowed for this question.")

            val createdComment: CreateComment = call.receive()
            if (createdComment.content.isEmpty()) badRequest("No comment content.")
            if (room.commentTemplate.isNotBlank() && createdComment.content.trim() == room.commentTemplate.trim())
                badRequest("Please modify the comment template before posting.")

            val prediction = if (createdComment.attachPrediction) {
                serverState.userPred[question.ref]?.get(user.ref)
            } else { null }

            val comment = QuestionComment(question = question.ref, user = user.ref, timestamp = unixNow(),
                content = createdComment.content, prediction = prediction)
            serverState.questionCommentManager.insertEntity(comment)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    postST("$questionUrl$commentUrl/edit") {
        call.withQuestion {
            val id = call.parameters["cID"]
            val comment = serverState.questionComments[question.ref]?.get(id)
                ?: notFound("No such comment.")

            val newContent: String = call.receive()
            if (newContent.isEmpty()) badRequest("No comment content.")

            if (!room.hasPermission(
                    user,
                    RoomPermission.MANAGE_COMMENTS
                ) && !(comment.user eqid user)
            ) unauthorized("No rights.")

            serverState.questionCommentManager.modifyEntity(comment.ref) {
                it.copy(content = newContent, modified = unixNow())
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    deleteST("$questionUrl$commentUrl") {
        call.withQuestion {
            serverState.withMutationLock {
                val id = call.parameters["cID"]
                val comment = serverState.questionComments[question.ref]?.get(id)
                    ?: notFound("No such comment.")

                if (!room.hasPermission(
                        user,
                        RoomPermission.MANAGE_COMMENTS
                    ) && !(comment.user eqid user)
                ) unauthorized("No rights.")

                serverState.withTransaction {
                    serverState.questionCommentManager.deleteEntity(comment, true)
                    serverState.commentLikeManager.deleteAllCommentLikes(comment.ref)
                }
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("$questionUrl$commentUrl/like") {
        call.withQuestion {
            val id = call.parameters["cID"] ?: ""
            val state = call.receive<Boolean>()

            assertPermission(RoomPermission.VIEW_QUESTION_COMMENTS, "You cannot like this comment.")
            val comment = serverState.questionComments[question.ref]?.get(id) ?: notFound("No such comment.")

            serverState.commentLikeManager.setLike(comment.ref, user.ref, state)
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}

fun roomCommentsRoutes(routing: Routing) = routing.apply {
    getWS("/state$roomUrl/comments") {
        call.withRoom {
            assertPermission( RoomPermission.VIEW_ROOM_COMMENTS, "You cannot view the discussion.")

            (serverState.roomComments[room.ref] ?: emptyMap()).values.map { comment->
                makeCommentPresentation(comment, null, user, room)
            }
        }
    }

    postST("$roomUrl/comments/add") {
        call.withRoom {
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
    postST("$roomUrl/comments/{cID}/edit") {
        call.withRoom {
            val id = call.parameters["cID"]
            val comment = serverState.roomComments[room.ref]?.get(id) ?: notFound("No such comment.")

            val newContent: String = call.receive()
            if (newContent.isEmpty()) unauthorized("No comment content.")

            if (!room.hasPermission(
                    user,
                    RoomPermission.MANAGE_COMMENTS
                ) && !(comment.user eqid user)
            ) unauthorized("You cannot delete this comment.")

            serverState.roomCommentManager.modifyEntity(comment.ref) {
                it.copy(content = newContent, modified = unixNow())
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    deleteST("$roomUrl/comments/{cID}") {
        call.withRoom {
            serverState.withMutationLock {
                val id = call.parameters["cID"]
                val comment = serverState.roomComments[room.ref]?.get(id) ?: notFound("No such comment.")

                if (!room.hasPermission(
                        user,
                        RoomPermission.MANAGE_COMMENTS
                    ) && !(comment.user eqid user)
                ) unauthorized("You cannot delete this comment.")

                serverState.withTransaction {
                    serverState.roomCommentManager.deleteEntity(comment, true)
                    // Delete all likes on this comment
                    serverState.commentLikeManager.deleteAllCommentLikes(comment.ref)
                }
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("$roomUrl/comments/{cID}/like") {
        call.withRoom {
            val id = call.parameters["cID"] ?: ""
            val state = call.receive<Boolean>()

            assertPermission(RoomPermission.VIEW_ROOM_COMMENTS, "You cannot like this comment.")
            val comment = serverState.roomComments[room.ref]?.get(id) ?: notFound("No such comment.")

            serverState.commentLikeManager.setLike(comment.ref, user.ref, state)
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}
