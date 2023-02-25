package components.redesign.questions

import components.AppStateContext
import components.rooms.RoomContext
import hooks.useDocumentTitle
import kotlinx.js.get
import react.FC
import react.Props
import react.router.useNavigate
import react.router.useParams
import react.useContext
import react.useEffect

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
        this.question = question
    }
}
