package tools.confido.web.components.questions.predictions

import tools.confido.web.components.basic.sansSerif
import tools.confido.web.utils.panzoom1d.ZoomHint
import web.cssom.*
import emotion.react.*
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.ReactHTML.div
import web.timers.clearTimeout
import web.timers.setTimeout
import kotlin.time.Duration.Companion.milliseconds

/** Renders [ZoomHint]'s cursor-anchored hint; mounted once, at the app root. */
val ZoomHintToast = FC<Props>("ZoomHintToast") {
    var pos by useState<Pair<Double, Double>>()
    useEffectWithCleanup(Unit) {
        var timer: web.timers.Timeout? = null
        ZoomHint.onShow = { p ->
            timer?.let { clearTimeout(it) }
            pos = p
            if (p != null) timer = setTimeout(2000.milliseconds) { pos = null }
        }
        onCleanup {
            ZoomHint.onShow = {}
            timer?.let { clearTimeout(it) }
        }
    }
    pos?.let { (x, y) ->
        div {
            css {
                position = Position.fixed
                zIndex = integer(2000)
                pointerEvents = None.none
                backgroundColor = Color("rgba(40,40,40,0.88)")
                color = NamedColor.white
                fontFamily = sansSerif
                fontSize = 12.px
                lineHeight = 16.px
                padding = Padding(4.px, 8.px)
                borderRadius = 5.px
                whiteSpace = WhiteSpace.nowrap
            }
            style = jso {
                left = (x + 14).px
                top = (y + 14).px
            }
            +"${ZoomHint.modifierLabel} + scroll to zoom"
        }
    }
}
