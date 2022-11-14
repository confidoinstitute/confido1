package tools.confido.question

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.refs.Entity
import tools.confido.refs.HasId
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref
import users.User

@Serializable
sealed class Comment : ImmediateDerefEntity{ // FIXME: probably load comments only on demand?
    abstract val user: Ref<User>
    abstract val timestamp: Int
    abstract val content: String
    fun key() = "${user.id}__${timestamp}"
}
@Serializable
data class QuestionComment(
    @SerialName("_id")
    override val id: String = "",
    override val user: Ref<User>,
    override val timestamp: Int,
    override val content: String,
    val question: Ref<Question>,
    val prediction: Prediction?,
) : Comment()

@Serializable
data class RoomComment(
    @SerialName("_id")
    override val id: String,
    override val user: Ref<User>,
    override val timestamp: Int,
    override val content: String,
    val room: Ref<Room>,
    val isAnnotation: Boolean,
) : Comment()

data class CommentLike(
    @SerialName("_id")
    override val id: String,
    val comment: Ref<Comment>,
    val user: Ref<User>,
) : ImmediateDerefEntity
