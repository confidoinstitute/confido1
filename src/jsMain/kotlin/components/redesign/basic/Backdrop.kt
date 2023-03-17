package components.redesign.basic

import browser.document
import components.redesign.transitions.Fade
import csstype.*
import dom.html.HTMLDivElement
import emotion.react.Global
import emotion.react.styles
import react.*
import react.dom.createPortal
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.div
import utils.except

external interface BackdropProps: HTMLAttributes<HTMLDivElement> {
    var appear: Boolean?
    var `in`: Boolean
}

val BackdropContext = createContext<StateSetter<Boolean>>()

val BackdropProvider = FC<PropsWithChildren> {
    val (shown, setShown) = useState(false)
    Backdrop {
        appear = false
        this.`in` = shown
    }
    BackdropContext.Provider {
        value = setShown
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
