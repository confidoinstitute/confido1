package components.redesign.questions

import components.AppStateContext
import kotlinx.js.get
import react.FC
import react.Props
import react.router.useParams
import react.useContext

val Question = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)

    val questionID = useParams()["questionID"] ?: return@FC
    val question = appState.questions[questionID] ?: return@FC

    QuestionPage {
        this.question = question
    }
}
