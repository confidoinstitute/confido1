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
import web.events.Event

external interface BackdropProps: HTMLAttributes<HTMLDivElement> {
    var appear: Boolean?
    var `in`: Boolean
}

typealias BackdropClickHandler = ()->Unit
class BackdropCounter(val add: (String, BackdropClickHandler?) -> Unit, val del: (String) -> Unit)

val BackdropContext = createContext<BackdropCounter>()

val BackdropProvider = FC<PropsWithChildren> {
    val (counter, setCounter) = useState(mapOf<String, BackdropClickHandler?>())

    val counterSetter = useMemo {
        BackdropCounter(
            add = { id, onClick -> setCounter { it.plus(id to onClick) } },
            del = { id -> setCounter { it.minus(id) } },
        )
    }

    Backdrop {
        appear = false
        this.`in` = counter.isNotEmpty()
        onClick = {
            // click directly on backdrop, not descendants
            if (it.target == it.currentTarget)
            counter.entries.forEach { it.value?.invoke() }
        }
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
                        left = 0.px
                        right = 0.px
                        bottom = 0.px
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

fun useBackdrop(open: Boolean, onClick: BackdropClickHandler? = null) {
    val setBackground = useContext(BackdropContext)
    val dialogId = useId()
    useEffect(open, onClick) {
        if (open) setBackground.add(dialogId, onClick) else setBackground.del(dialogId)
        cleanup {
            setBackground.del(dialogId)
        }
    }
}