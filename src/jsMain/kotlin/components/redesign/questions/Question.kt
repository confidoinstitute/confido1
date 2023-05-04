package components.redesign.questions

import components.*
import components.rooms.*
import kotlinx.js.*
import react.*
import react.router.*

val Question = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val roomCtx = useContext(RoomContext)

    val questionID = useParams()["questionID"] ?: return@FC
    val question = appState.questions[questionID]
    val navigate = useNavigate()
    useEffect(question) {
        if (question == null) navigate(roomCtx.urlPrefix)
    }

    if (question != null)
    QuestionPage {
        key = questionID
        this.question = question
    }
}
