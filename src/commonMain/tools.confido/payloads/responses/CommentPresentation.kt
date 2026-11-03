package tools.confido.payloads.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Comment
import tools.confido.question.CommentScope
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.users.UserPresentation

@Serializable
sealed class CommentExtraData
@Serializable
@SerialName("question")
data class QuestionCommentExtraData(val dist: ProbabilityDistribution?) : CommentExtraData()

/**
 * Comment in a form for presentation to the user
 */
@Serializable
data class CommentPresentation<out T: CommentExtraData>(
    val commentId: Ref<Comment>,
    val scope: CommentScope,
    val user: UserPresentation,
    val timestamp: Int,
    val content: String,
    val modified: Int? = null,
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    val extraData: T? = null,
){
    val isMine get() = user.isMyself
}

