package components.redesign.basic

import components.*
import components.redesign.transitions.*
import csstype.*
import dom.html.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import web.timers.*
import kotlin.time.Duration.Companion.seconds

val GlobalErrorMessage = FC<PropsWithChildren> {

    var errorRef = useRef<HTMLDivElement>()
    var errorOpen by useState(false)
    var errorMessage by useState<String?>(null)

    val (queuedErrors, setQueuedErrors) = useState(emptyList<String>())

    useEffectOnce {
        if (showError != null)
            console.error("There can be only one GlobalErrorMessage component!")

        showError = { message ->
            console.log("Queueing error", message)
            setQueuedErrors {
                it + listOf(message)
            }
            console.log("Queue contains", queuedErrors.toTypedArray())
        }

        cleanup {
            showError = null
        }
    }

    useEffect(queuedErrors, errorMessage) {
        if (errorMessage == null && queuedErrors.isNotEmpty()) {
            console.log("Queue contains", queuedErrors.toTypedArray())
            console.log("Popping error", queuedErrors[0])
            errorOpen = true
            errorMessage = queuedErrors[0]
            setQueuedErrors {
                it.drop(1)
            }
        }
    }

    Slide {
        this.direction = SlideDirection.up
        this.nodeRef = errorRef
        this.`in` = errorOpen
        timeout = 100
        onEntered = { _, _ ->
            setTimeout(5.seconds) { errorOpen = false }
        }
        onExited = { _, _ ->
            errorMessage = null
        }
        Fragment {
            errorMessage?.let {
                div {
                    ref = errorRef
                    css {
                        position = Position.fixed
                        width = 100.pct
                        bottom = 0.px
                        zIndex = integer(3000)
                    }
                    div {
                        css {
                            margin = 25.px
                            padding = Padding(8.px, 10.px)
                            borderRadius = 5.px
                            fontSize = 12.px
                            lineHeight = 15.px

                            backgroundColor = Color("#000000")
                            color = Color("#FFFFFF")
                            fontFamily = sansSerif

                        }
                        b {+"Error: "}
                        +it
                    }
                }
            }
        }
    }
}
