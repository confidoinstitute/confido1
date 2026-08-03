package tools.confido.question

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.refs.Entity
import tools.confido.refs.Ref
import tools.confido.utils.HasUrlPrefix
import tools.confido.utils.unixNow
import users.User

/**
 * Comments are non-resident on the server (fetched per question/room partition from the
 * comment archive) and never reach the client as entities — only as CommentPresentation
 * payloads. Hence plain [Entity], not ImmediateDerefEntity: a Ref<Comment> cannot be
 * dereferenced synchronously, by construction.
 *
 * Likes are stored directly on the comment ([likes]) — they are read almost exclusively
 * per-comment (like count, liked-by-me) and travel with the comment payload.
 */
@Serializable
sealed class Comment : Entity, HasUrlPrefix {
    abstract val user: Ref<User>
    abstract val timestamp: Int
    abstract val content: String
    abstract val modified: Int?
    abstract val likes: List<Ref<User>>
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
    override val likes: List<Ref<User>> = emptyList(),
) : Comment() {
    override val urlPrefix get() = "${Question.urlPrefix(question.id)}/comments/$id"
}

@Serializable
data class RoomComment(
    @SerialName("_id")
    override val id: String,
    override val user: Ref<User>,
    @EncodeDefault
    override val timestamp: Int = unixNow(),
    override val content: String,
    override val modified: Int? = null,
    val room: Ref<Room>,
    val isAnnotation: Boolean = false,
    override val likes: List<Ref<User>> = emptyList(),
) : Comment() {
    override val urlPrefix get() = "${Room.urlPrefix(room.id)}/comments/$id"
}
