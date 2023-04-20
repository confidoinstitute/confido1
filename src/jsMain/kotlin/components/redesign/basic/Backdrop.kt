package components.redesign.basic

import browser.*
import components.redesign.transitions.*
import csstype.*
import dom.html.*
import emotion.react.*
import react.*
import react.dom.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import utils.*

external interface BackdropProps: HTMLAttributes<HTMLDivElement> {
    var appear: Boolean?
    var `in`: Boolean
}

class BackdropCounter(val add: (String) -> Unit, val del: (String) -> Unit)

val BackdropContext = createContext<BackdropCounter>()

val BackdropProvider = FC<PropsWithChildren> {
    val (counter, setCounter) = useState(setOf<String>())

    val counterSetter = useMemo {
        BackdropCounter(
            add = { id -> setCounter { it.plus(id) } },
            del = { id -> setCounter { it.minus(id) } },
        )
    }

    Backdrop {
        appear = false
        this.`in` = counter.isNotEmpty()
    }
    BackdropContext.Provider {
        value = counterSetter
        +it.children
    }
}

val Backdrop = FC<BackdropProps> {props ->
    val nodeRef = useRef<HTMLDivElement>()
    +createPortal(
        Fade.create {
            this.`in` = props.`in`
            timeout = 250
            appear = props.appear ?: true
            mountOnEnter = true
            unmountOnExit = true
            this.nodeRef = nodeRef
            Fragment {
                div {
                    ref = nodeRef
                    +props.except("in", "appear")
                    css(override = props) {
                        position = Position.fixed
                        top = 0.px
                        width = 100.pct
                        height = 100.pct
                        overflow = Overflow.hidden
                        backgroundColor = rgba(0, 0, 0, 0.5)
                        zIndex = integer(2000)
                    }
                }
                Global {
                    styles {
                        "body" {
                            overflow = Overflow.hidden
                        }
                    }
                }
            }
        }, document.body.asDynamic())
}

fun useBackdrop(open: Boolean) {
    val setBackground = useContext(BackdropContext)
    val dialogId = useId()
    useEffect(open) {
        if (open) setBackground.add(dialogId) else setBackground.del(dialogId)
        cleanup {
            setBackground.del(dialogId)
        }
    }
}