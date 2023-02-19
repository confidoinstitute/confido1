package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import payloads.requests.CreateComment
import payloads.responses.CommentInfo
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.sessions.TransientData
import tools.confido.question.Comment
import tools.confido.question.Question
import tools.confido.question.QuestionComment
import tools.confido.question.RoomComment
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

fun makeCommentInfo(user: User, comments: Map<String, Comment>?) = comments?.mapValues {
    val comment = it.value
    CommentInfo(
        comment,
        serverState.commentLikeManager.numLikes[comment.ref] ?: 0,
        comment.ref in (serverState.commentLikeManager.byUser[user.ref] ?: emptySet())
    )
} ?: emptyMap()

inline fun <reified T: Entity> makeCommentInfo(user: User, entity: T) = makeCommentInfo(user, commentList(entity))

fun questionCommentsRoutes(routing: Routing) = routing.apply {
    // View comments
    getWS("/state$questionUrl/comments") {
        withQuestion {
            assertPermission(RoomPermission.VIEW_QUESTION_COMMENTS, "You cannot view the discussion for this question.")

            val commentInfo = makeCommentInfo(user, question)
            commentInfo
        }
    }

    postST("$questionUrl/comments/add") {
        withQuestion {
            assertPermission(RoomPermission.POST_QUESTION_COMMENT, "You cannot post a comment to this question.")

            val createdComment: CreateComment = call.receive()
            if (createdComment.content.isEmpty()) badRequest("No comment content.")

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
        withQuestion {
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
        withQuestion {
            serverState.withMutationLock {
                val id = call.parameters["cID"]
                val comment = serverState.questionComments[question.ref]?.get(id)
                    ?: notFound("No such comment.")

                if (!room.hasPermission(
                        user,
                        RoomPermission.MANAGE_COMMENTS
                    ) && !(comment.user eqid user)
                ) unauthorized("No rights.")

                serverState.questionCommentManager.deleteEntity(comment, true)
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("$questionUrl$commentUrl/like") {
        withQuestion {
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
        withRoom {
            assertPermission( RoomPermission.VIEW_ROOM_COMMENTS, "You cannot view the discussion.")

            val commentInfo = makeCommentInfo(user, room)
            commentInfo
        }
    }

    postST("$roomUrl/comments/add") {
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
    postST("$roomUrl/comments/{cID}/edit") {
        withRoom {
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
    postST("$roomUrl/comments/{cID}/like") {
        withRoom {
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
