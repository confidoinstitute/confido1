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
import tools.confido.state.serverState
import tools.confido.store.questionCommentArchive
import tools.confido.store.roomCommentArchive
import tools.confido.store.transact
import tools.confido.utils.unixNow
import users.User

const val commentUrl = "/comments/{cID}"

fun<T: CommentExtraData> makeCommentPresentation(comment: Comment, extraData: T?, observer: User, room: Room) =
    CommentPresentation(
        comment.ref,
        comment.urlPrefix,
        getUserPresentation(comment.user.deref(), observer, UserPresentationContext.COMMENT_AUTHOR, room),
        comment.timestamp,
        comment.content,
        comment.modified,
        comment.likes.size,
        observer.ref in comment.likes,
        extraData
    )

fun questionCommentsRoutes(routing: Routing) = routing.apply {
    // View comments
    topicWS("/state$questionUrl/comments") {
        call.withQuestion {
            assertPermission(RoomPermission.VIEW_QUESTION_COMMENTS, "You cannot view the discussion for this question.")

            val canViewAll = room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
            val lastPrediction = serverState.userPred[ref]?.get(user.ref)
            val canView = when (question.commentVisibility) {
                CommentVisibility.EVERYONE -> true
                CommentVisibility.ANSWERED -> lastPrediction != null
                CommentVisibility.MODERATOR_ONLY -> false
            }

            questionCommentArchive.all(ref).filter { comment ->
                comment.user == user.ref || (canViewAll || canView)
            }.map { comment ->
                makeCommentPresentation(comment, QuestionCommentExtraData(comment.prediction?.dist), user, room)
            }
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
            questionCommentArchive.insert(comment)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    postST("$questionUrl$commentUrl/edit") {
        call.withQuestion {
            val id = call.parameters["cID"] ?: ""
            val newContent: String = call.receive()
            if (newContent.isEmpty()) badRequest("No comment content.")

            transact {
                val comment = questionCommentArchive.get(question.ref, id) ?: notFound("No such comment.")
                if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user))
                    unauthorized("No rights.")
                questionCommentArchive.update(comment, comment.copy(content = newContent, modified = unixNow()))
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    deleteST("$questionUrl$commentUrl") {
        call.withQuestion {
            val id = call.parameters["cID"] ?: ""
            transact {
                val comment = questionCommentArchive.get(question.ref, id) ?: notFound("No such comment.")
                if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user))
                    unauthorized("No rights.")
                questionCommentArchive.delete(comment) // likes are part of the document
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
            transact {
                val comment = questionCommentArchive.get(question.ref, id) ?: notFound("No such comment.")
                if (comment.user == user.ref) forbidden("You cannot upvote your own comment")
                val newLikes = if (state) (comment.likes + user.ref).distinct() else comment.likes - user.ref
                if (newLikes != comment.likes)
                    questionCommentArchive.update(comment, comment.copy(likes = newLikes))
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}

fun roomCommentsRoutes(routing: Routing) = routing.apply {
    topicWS("/state$roomUrl/comments") {
        call.withRoom {
            assertPermission( RoomPermission.VIEW_ROOM_COMMENTS, "You cannot view the discussion.")

            roomCommentArchive.all(room.ref).map { comment->
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
            roomCommentArchive.insert(comment)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("$roomUrl/comments/{cID}/edit") {
        call.withRoom {
            val id = call.parameters["cID"] ?: ""
            val newContent: String = call.receive()
            if (newContent.isEmpty()) unauthorized("No comment content.")

            transact {
                val comment = roomCommentArchive.get(room.ref, id) ?: notFound("No such comment.")
                if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user))
                    unauthorized("You cannot edit this comment.")
                roomCommentArchive.update(comment, comment.copy(content = newContent, modified = unixNow()))
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    deleteST("$roomUrl/comments/{cID}") {
        call.withRoom {
            val id = call.parameters["cID"] ?: ""
            transact {
                val comment = roomCommentArchive.get(room.ref, id) ?: notFound("No such comment.")
                if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user))
                    unauthorized("You cannot delete this comment.")
                roomCommentArchive.delete(comment)
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
            transact {
                val comment = roomCommentArchive.get(room.ref, id) ?: notFound("No such comment.")
                if (comment.user == user.ref) forbidden("You cannot upvote your own comment")
                val newLikes = if (state) (comment.likes + user.ref).distinct() else comment.likes - user.ref
                if (newLikes != comment.likes)
                    roomCommentArchive.update(comment, comment.copy(likes = newLikes))
            }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}
