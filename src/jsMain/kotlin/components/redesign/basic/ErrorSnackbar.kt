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

    val ref = useRef<HTMLDivElement>()
    var isOpen by useState(false)
    var currentMsg by useState<Message?>(null)

    val (queuedMsgs, setQueuedMsgs) = useState(emptyList<Message>())

    useEffectOnce {
        showMessage = { message ->
            console.log("Queueing error", message)
            setQueuedMsgs {
                it + listOf(message)
            }
            console.log("Queue contains", queuedMsgs.toTypedArray())
        }

        cleanup {
            showMessage = {}
        }
    }

    useEffect(queuedMsgs, currentMsg) {
        if (currentMsg == null && queuedMsgs.isNotEmpty()) {
            console.log("Queue contains", queuedMsgs.toTypedArray())
            console.log("Popping msg", queuedMsgs[0])
            isOpen = true
            currentMsg = queuedMsgs[0]
            setQueuedMsgs {
                it.drop(1)
            }
        }
    }

    Slide {
        this.direction = SlideDirection.up
        this.nodeRef = ref
        this.`in` = isOpen
        timeout = 100
        onEntered = { _, _ ->
            setTimeout(5.seconds) { isOpen = false }
        }
        onExited = { _, _ ->
            currentMsg = null
        }
        Fragment {
            currentMsg?.let {
                div {
                    this.ref = ref
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
                        if (it.type == MessageType.ERROR)
                            b {+"Error: "}
                        +it.text
                    }
                }
            }
        }
    }
}
