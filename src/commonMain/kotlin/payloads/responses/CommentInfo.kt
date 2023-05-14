package payloads.responses

import kotlinx.serialization.Serializable
import tools.confido.question.Comment

/**
 * Comment info sent as a list item by the comment subscription channel, contains the [comment] itself, its [likes count][likeCount] and whether the client [likes it][likedByMe].
 */
@Serializable
data class CommentInfo(
    val comment: Comment,
    val likeCount: Int,
    val likedByMe: Boolean,
)