package tools.confido.application.routes

import io.ktor.server.routing.*
import tools.confido.payloads.responses.CommentExtraData
import tools.confido.payloads.responses.CommentPresentation
import tools.confido.payloads.responses.QuestionCommentExtraData
import tools.confido.rooms.AttributionLevel
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.api.Api
import tools.confido.application.UserPresentationContext
import tools.confido.application.getUserPresentation
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.userSession
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.state.serverState
import tools.confido.store.*
import tools.confido.utils.unixNow
import tools.confido.users.User
import tools.confido.state.Topics

fun<T: CommentExtraData> makeCommentPresentation(comment: Comment, extraData: T?, observer: User, room: Room) =
    CommentPresentation(
        comment.ref,
        comment.scope,
        // Question comments mask under the question's effective policy; room
        // comments (question == null) under the room default.
        getUserPresentation(comment.user.deref(), observer, UserPresentationContext.COMMENT_AUTHOR, room,
                            (comment as? QuestionComment)?.question?.deref()),
        comment.timestamp,
        comment.content,
        comment.modified,
        comment.likes.size,
        observer.ref in comment.likes,
        extraData
    )

fun questionCommentsRoutes(routing: Routing) = routing.apply {
    // Comment topics are plain View compositions over a pinned resident partition
    // (ResidentPartitions): the build acquires the pin (first subscriber loads the
    // partition, everyone else reads memory) and releases it via the subscription's
    // resource hook. Payloads are per-viewer (likedByMe, own-comment visibility), but
    // the fetch amplification is gone and the payload is frame-consistent with the
    // comment-count folds (same synchronous change dispatch). The users map is an
    // input for author presentations.
    viewWS(Topics.questionComments) { qRef ->
        val viewerRef = call.userSession?.userRef ?: unauthorized("Not logged in.")
        val pin = questionCommentArchive.partitions.acquire(qRef)
        resources.onClose { pin.close() }
        val roomView = roomStore.questionRoomState.asView().at(qRef)
            .flatMap { rRef -> rRef?.let { roomStore.state.asView().at(it.id) } ?: viewOf(null) }
        combineViews(
            questionStore.state.asView().at(qRef.id),
            roomView,
            userStore.state.asView(),
            latestPredictions.state.asView().map { it[qRef]?.get(viewerRef) },
            pin.view,
        ) { question, room, users, myPred, comments ->
            val viewer = users[viewerRef.id] ?: unauthorized("Not logged in.")
            if (question == null || room == null) notFound("No such question.")
            if (!question.visible && !room.hasPermission(viewer, RoomPermission.VIEW_HIDDEN_QUESTIONS))
                notFound("No such question.")
            if (!room.hasPermission(viewer, RoomPermission.VIEW_QUESTION_COMMENTS))
                forbidden("You cannot view the discussion for this question.")
            val canViewAll = room.hasPermission(viewer, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
            val canView = when (question.commentVisibility) {
                CommentVisibility.EVERYONE -> true
                CommentVisibility.ANSWERED -> myPred != null
                CommentVisibility.MODERATOR_ONLY -> false
            }
            val canSeeOthers = canViewAll || canView
            comments.values.sortedBy { it.timestamp }
                .filter { it.user == viewer.ref || canSeeOthers }
                .map { comment ->
                    // On an unattributed (ANONYMOUS+) question, others' attached predictions
                    // stay hidden (attachment is blocked there; this covers comments that
                    // predate the policy).
                    val dist = comment.prediction?.dist
                        ?.takeIf { question.resolvedAttributionIn(room).level < AttributionLevel.ANONYMOUS
                                   || comment.user == viewer.ref }
                    makeCommentPresentation(comment, QuestionCommentExtraData(dist), viewer, room)
                }
        }
    }

    apiST(Api.questionCommentAdd) { qRef, createdComment ->
        call.withQuestion(qRef) {
            assertVisible()
            assertPermission(RoomPermission.POST_QUESTION_COMMENT, "You cannot post a comment to this question.")

            if (!question.allowComments) unauthorized("Commenting is not allowed for this question.")

            if (createdComment.content.isEmpty()) badRequest("No comment content.")
            if (room.commentTemplate.isNotBlank() && createdComment.content.trim() == room.commentTemplate.trim())
                badRequest("Please modify the comment template before posting.")

            if (createdComment.attachPrediction
                && question.resolvedAttributionIn(room).level >= AttributionLevel.ANONYMOUS)
                badRequest("Predictions cannot be attached to comments on this question.")
            val prediction = if (createdComment.attachPrediction) {
                serverState.userPred[question.ref]?.get(user.ref)
            } else { null }

            val comment = QuestionComment(question = question.ref, user = user.ref, timestamp = unixNow(),
                content = createdComment.content, prediction = prediction)
            questionCommentArchive.insert(comment)
        }
    }

    apiST(Api.questionCommentEdit) { qRef, id, newContent ->
        call.withQuestion(qRef) {
            assertVisible()
            if (newContent.isEmpty()) badRequest("No comment content.")

            transact {
                val comment = questionCommentArchive.get(question.ref, id) ?: notFound("No such comment.")
                if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user))
                    unauthorized("No rights.")
                questionCommentArchive.update(comment, comment.copy(content = newContent, modified = unixNow()))
            }
        }
    }

    apiST(Api.questionCommentDelete) { qRef, id, _ ->
        call.withQuestion(qRef) {
            assertVisible()
            transact {
                val comment = questionCommentArchive.get(question.ref, id) ?: notFound("No such comment.")
                if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user))
                    unauthorized("No rights.")
                questionCommentArchive.delete(comment) // likes are part of the document
            }
        }
    }

    apiST(Api.questionCommentLike) { qRef, id, state ->
        call.withQuestion(qRef) {
            assertVisible()
            assertPermission(RoomPermission.VIEW_QUESTION_COMMENTS, "You cannot like this comment.")
            transact {
                val comment = questionCommentArchive.get(question.ref, id) ?: notFound("No such comment.")
                if (comment.user == user.ref) forbidden("You cannot upvote your own comment")
                val newLikes = if (state) (comment.likes + user.ref).distinct() else comment.likes - user.ref
                if (newLikes != comment.likes)
                    questionCommentArchive.update(comment, comment.copy(likes = newLikes))
            }
        }
    }
}

fun roomCommentsRoutes(routing: Routing) = routing.apply {
    viewWS(Topics.roomComments) { rRef ->
        val viewerRef = call.userSession?.userRef ?: unauthorized("Not logged in.")
        val pin = roomCommentArchive.partitions.acquire(rRef)
        resources.onClose { pin.close() }
        combineViews(
            roomStore.state.asView().at(rRef.id),
            userStore.state.asView(),
            pin.view,
        ) { room, users, comments ->
            val viewer = users[viewerRef.id] ?: unauthorized("Not logged in.")
            if (room == null) notFound("No such room.")
            if (!room.hasPermission(viewer, RoomPermission.VIEW_ROOM_COMMENTS))
                forbidden("You cannot view the discussion.")
            comments.values.sortedBy { it.timestamp }
                .map { makeCommentPresentation(it, null, viewer, room) }
        }
    }

    apiST(Api.roomCommentAdd) { rRef, createdComment ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.POST_ROOM_COMMENT, "You cannot add comments to this room.")

            if (createdComment.content.isEmpty()) badRequest("No comment content.")

            val comment = RoomComment(id = "", room = room.ref, user = user.ref, timestamp = unixNow(),
                content = createdComment.content, isAnnotation = false)
            roomCommentArchive.insert(comment)
        }
    }
    apiST(Api.roomCommentEdit) { rRef, id, newContent ->
        call.withRoom(rRef) {
            if (newContent.isEmpty()) unauthorized("No comment content.")

            transact {
                val comment = roomCommentArchive.get(room.ref, id) ?: notFound("No such comment.")
                if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user))
                    unauthorized("You cannot edit this comment.")
                roomCommentArchive.update(comment, comment.copy(content = newContent, modified = unixNow()))
            }
        }
    }
    apiST(Api.roomCommentDelete) { rRef, id, _ ->
        call.withRoom(rRef) {
            transact {
                val comment = roomCommentArchive.get(room.ref, id) ?: notFound("No such comment.")
                if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user))
                    unauthorized("You cannot delete this comment.")
                roomCommentArchive.delete(comment)
            }
        }
    }
    apiST(Api.roomCommentLike) { rRef, id, state ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.VIEW_ROOM_COMMENTS, "You cannot like this comment.")
            transact {
                val comment = roomCommentArchive.get(room.ref, id) ?: notFound("No such comment.")
                if (comment.user == user.ref) forbidden("You cannot upvote your own comment")
                val newLikes = if (state) (comment.likes + user.ref).distinct() else comment.likes - user.ref
                if (newLikes != comment.likes)
                    roomCommentArchive.update(comment, comment.copy(likes = newLikes))
            }
        }
    }
}
