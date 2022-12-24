package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.refs.Ref
import users.User

@Serializable
sealed class PresenterView {

}

@Serializable
@SerialName("question")
data class QuestionPV(
    val question: Ref<Question>
) : PresenterView()

@Serializable
data class PresenterInfo(
    val token : String,
    val user: Ref<User>,
    val view: PresenterView? = null,
    val activePresenterWindows : Int = 0,
) {
    val isPresenterWindowOpen get() = activePresenterWindows > 0
}