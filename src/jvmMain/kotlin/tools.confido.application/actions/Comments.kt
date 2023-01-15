package tools.confido.application.actions

import payloads.responses.CommentInfo
import rooms.Room
import tools.confido.question.Comment
import tools.confido.question.Question
import tools.confido.refs.*
import tools.confido.state.serverState
import users.User

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
