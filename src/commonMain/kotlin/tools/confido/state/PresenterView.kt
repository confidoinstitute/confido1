package tools.confido.state

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import rooms.Room
import tools.confido.extensions.Extension
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.utils.unixNow
import users.User

@Serializable
abstract class PresenterView {
    open suspend fun isValid() = true
    abstract fun describe(): String
}


@Serializable
@SerialName("empty")
object EmptyPV: PresenterView() {
    override fun describe() = "blank screen"
}

@Serializable
@SerialName("question")
data class QuestionPV(
    val question: Ref<Question>
) : PresenterView() {
    override suspend fun isValid() = question.deref() != null
    override fun describe() = "question text"
}

@Serializable
@SerialName("groupPred")
data class GroupPredPV(
    val question: Ref<Question>,
    val showResolution: Boolean = false,
) : PresenterView() {
    override suspend fun isValid() = question.deref() != null
    override fun describe() = "group predictions for this question"
}

@Serializable
@SerialName("inviteLink")
data class InviteLinkPV(
    val room: Ref<Room>,
    val id: String,
) : PresenterView() {
    override suspend fun isValid() = room.deref()?.inviteLinks?.any { it.id == this.id } ?: false
    override fun describe() = "invite link and QR code"
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

val presenterSM by lazy { SerializersModule {
    polymorphic(PresenterView::class) {
        subclass(EmptyPV::class)
        subclass(QuestionPV::class)
        subclass(GroupPredPV::class)
        subclass(InviteLinkPV::class)
        Extension.forEach { it.registerPresenterViews(this) }
    }
} }