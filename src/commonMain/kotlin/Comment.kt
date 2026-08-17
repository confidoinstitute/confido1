package tools.confido.question

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.refs.Entity
import tools.confido.refs.Ref
import tools.confido.utils.unixNow
import users.User

/**
 * Where a comment lives (question or room discussion), as typed refs. Travels in
 * CommentPresentation so the client can bind the comment's API endpoints
 * (tools.confido.api) from refs instead of a server-composed URL string.
 */
@Serializable
sealed class CommentScope {
    @Serializable @SerialName("question")
    data class QuestionScope(val question: Ref<Question>) : CommentScope()
    @Serializable @SerialName("room")
    data class RoomScope(val room: Ref<Room>) : CommentScope()
}

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
sealed class Comment : Entity {
    abstract val user: Ref<User>
    abstract val timestamp: Int
    abstract val content: String
    abstract val modified: Int?
    abstract val likes: List<Ref<User>>
    abstract val scope: CommentScope
}
@Serializable
@SerialName("question")
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
    override val scope get() = CommentScope.QuestionScope(question)
}

@Serializable
@SerialName("room")
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
    override val scope get() = CommentScope.RoomScope(room)
}
