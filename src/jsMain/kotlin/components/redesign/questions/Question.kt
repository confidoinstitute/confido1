package components.redesign.questions

import web.window.window
import components.*
import components.redesign.rooms.RoomContext
import react.*
import react.router.*

external interface QuestionProps : Props {
    var openResolve: Boolean?
}

val Question = FC<QuestionProps> { props->
    val (appState, stale) = use(AppStateContext)!!
    val roomCtx = use(RoomContext)!!

    val questionID = useParams()["questionID"] ?: return@FC
    val question = appState.questions[questionID]
    val navigate = useNavigate()
    useEffect(question) {
        if (question == null) navigate(roomCtx.urlPrefix)
    }

    useEffectWithCleanup(questionID) {
        window.scrollTo(0.0, 0.0)
        onCleanup {
            window.scrollTo(0.0, 0.0)
        }
    }

    if (question != null)
    QuestionPage {
        key = questionID
        this.question = question
        this.openResolve = props.openResolve
    }
}
