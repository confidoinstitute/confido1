package components.redesign.questions

import components.*
import components.redesign.basic.sansSerif
import components.redesign.rooms.RoomContext
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import react.router.*
import react.router.dom.Link
import web.cssom.*
import web.timers.clearTimeout
import web.timers.setTimeout
import web.window.window
import kotlin.time.Duration.Companion.milliseconds

external interface QuestionProps : Props {
    var openResolve: Boolean?
}

val Question = FC<QuestionProps> { props->
    val (appState, stale) = use(AppStateContext)!!
    val roomCtx = use(RoomContext)!!

    val questionID = useParams()["questionID"] ?: return@FC
    val question = appState.questions[questionID]

    // Show "not available" after a short debounce when question is null.
    // The delay avoids a flash when the question is about to appear from a pending state update
    // (e.g. presentation navigates to a question whose state update hasn't arrived yet).
    var showNotFound by useState(false)
    useEffectWithCleanup(questionID, question == null) {
        showNotFound = false
        if (question == null) {
            val timer = setTimeout(400.milliseconds) { showNotFound = true }
            onCleanup { clearTimeout(timer) }
        }
    }

    useEffectWithCleanup(questionID) {
        window.scrollTo(0.0, 0.0)
        onCleanup {
            window.scrollTo(0.0, 0.0)
        }
    }

    if (question != null) {
        QuestionPage {
            key = questionID
            this.question = question
            this.openResolve = props.openResolve
        }
    } else if (showNotFound) {
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                gap = 16.px
                padding = Padding(60.px, 20.px)
                fontFamily = sansSerif
                textAlign = TextAlign.center
            }
            div {
                css {
                    fontSize = 18.px
                    lineHeight = 22.px
                    color = Color("#666")
                }
                +"This question is not available."
            }
            Link {
                to = roomCtx.urlPrefix
                css {
                    fontSize = 16.px
                    fontWeight = integer(600)
                    color = Color("#1976D2")
                    textDecoration = None.none
                }
                +"Back to room"
            }
        }
    }
}
