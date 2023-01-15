package payloads.responses

import kotlinx.serialization.Serializable
import tools.confido.question.Comment

@Serializable
data class CommentInfo(
    val comment: Comment,
    val likeCount: Int,
    val likedByMe: Boolean,
)