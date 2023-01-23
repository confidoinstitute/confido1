package tools.confido.state

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.utils.unixNow
import users.User

@Serializable
sealed class PresenterView {
    open suspend fun isValid() = true
}


@Serializable
@SerialName("empty")
object EmptyPV: PresenterView()

@Serializable
@SerialName("question")
data class QuestionPV(
    val question: Ref<Question>
) : PresenterView() {
    override suspend fun isValid() = question.deref() != null
}

@Serializable
@SerialName("inviteLink")
data class InviteLinkPV(
    val room: Ref<Room>,
    val id: String,
) : PresenterView() {
    override suspend fun isValid() = room.deref()?.inviteLinks?.any { it.id == this.id } ?: false
}


const val PRESENTER_LIFETIME = 30 * 60

@Serializable
data class PresenterInfo(
    //val token : String, // TODO: (later) think about link-sharable presenter views (e.g. in order to show it on
                          // another device
    //val user: Ref<User>,
    val view: PresenterView = EmptyPV,
    val lastUpdate: Int = unixNow(),
) {
    val isValid get() = unixNow() - lastUpdate <= PRESENTER_LIFETIME
}