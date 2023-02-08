package tools.confido.question

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref
import tools.confido.utils.HasUrlPrefix
import users.User

@Serializable
sealed class Comment : ImmediateDerefEntity, HasUrlPrefix { // FIXME: probably load comments only on demand?
    abstract val user: Ref<User>
    abstract val timestamp: Int
    abstract val content: String
    abstract val modified: Int?
    fun key() = "${user.id}__${timestamp}"
}
@Serializable
data class QuestionComment(
    @SerialName("_id")
    override val id: String = "",
    override val user: Ref<User>,
    override val timestamp: Int,
    override val content: String,
    override val modified: Int? = null,
    val question: Ref<Question>,
    val prediction: Prediction?,
) : Comment() {
    override val urlPrefix get() = "${Question.urlPrefix(question.id)}/comments/$id"
}

@Serializable
data class RoomComment(
    @SerialName("_id")
    override val id: String,
    override val user: Ref<User>,
    override val timestamp: Int,
    override val content: String,
    override val modified: Int? = null,
    val room: Ref<Room>,
    val isAnnotation: Boolean,
) : Comment() {
    override val urlPrefix get() = "${Room.urlPrefix(room.id)}/comments/$id"
}


@Serializable
data class CommentLike(
    @SerialName("_id")
    override val id: String,
    val comment: Ref<Comment>,
    val user: Ref<User>,
) : ImmediateDerefEntity
