package components.redesign.basic

import components.ConnectionContext
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.use
import react.useEffectWithCleanup
import react.useRef
import react.useState
import web.cssom.*
import web.timers.clearTimeout
import web.timers.setTimeout

/**
 * Global connection-status bar: names the disconnected state once ("reconnecting…"),
 * with a brief "back online" flash on recovery. Individual mutation affordances gray
 * out via [components.useDisconnected]; this banner is the explanation for why.
 */
val ConnectionBanner = FC<Props> {
    val disconnected = use(ConnectionContext) ?: false
    var flashOnline by useState(false)
    val wasDisconnected = useRef(false)

    useEffectWithCleanup(disconnected) {
        if (!disconnected && wasDisconnected.current == true) {
            flashOnline = true
            val timer = setTimeout({ flashOnline = false }, 2000)
            onCleanup { clearTimeout(timer) }
        }
        wasDisconnected.current = disconnected
    }

    if (!disconnected && !flashOnline) return@FC
    div {
        css {
            position = Position.fixed
            top = 0.px
            left = 0.px
            right = 0.px
            zIndex = integer(3000)
            padding = Padding(6.px, 15.px)
            textAlign = TextAlign.center
            fontFamily = sansSerif
            fontSize = 13.px
            fontWeight = integer(600)
            color = NamedColor.white
            backgroundColor = if (disconnected) Color("#dd3b3b") else Color("#3baf5d")
        }
        if (disconnected) +"Connection lost — reconnecting…"
        else +"Back online"
    }
}
