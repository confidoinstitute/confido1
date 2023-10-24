package components.redesign.calibration

import components.redesign.basic.*
import csstype.*
import emotion.react.css
import emotion.css.ClassName
import react.FC
import react.PropsWithClassName
import react.dom.html.ReactHTML.div
import react.dom.svg.AlignmentBaseline
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.clipPath
import react.dom.svg.ReactSVG.defs
import react.dom.svg.ReactSVG.g
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.rect
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.text
import tools.confido.calibration.CalibrationBin
import tools.confido.calibration.CalibrationEntry
import tools.confido.calibration.CalibrationVector
import tools.confido.utils.*
import utils.except

external interface CalibrationGraphProps: PropsWithElementSize, PropsWithClassName {
    var calib: CalibrationVector
    var height: Double?
}

val wellCalibratedRadius = 0.05
val slightMiscalibRadius = 0.2
private fun fmtp(p: Double) = (100*p).toFixed(1).trimEnd('0').trimEnd('.')+"%"

data class CalibrationBand(
    val range: ClosedFloatingPointRange<Double>,
    val color: String,
    val name: String,
)

val calibrationBands = listOf(
    CalibrationBand(-1.0..-slightMiscalibRadius, "#bc97f4", "Overconfident"),
    CalibrationBand(-slightMiscalibRadius..-wellCalibratedRadius, "#d0b7f5", "Slightly overconfident"),
    CalibrationBand(-wellCalibratedRadius..wellCalibratedRadius, "#f5fafa", "Well-calibrated"),
    CalibrationBand(wellCalibratedRadius..slightMiscalibRadius, "#b1ebf3", "Slightly overconfident"),
    CalibrationBand(slightMiscalibRadius..1.0, "#90e4f3", "Underconfident"),
)



private val CalibrationGraphContent = elementSizeWrapper(FC<CalibrationGraphProps> { props->
    val height = props.elementHeight
    val leftPad = 0.0
    val botPad = 0.0
    val topPad = 0.0
    val rightPad = 0.0
    val graphWidth = props.elementWidth - leftPad - rightPad
    val graphHeight = height - botPad - topPad
    val origin = List2(leftPad, height - botPad)
    fun proj(px: Double, py: Double) = origin `Z+` List2(
        (px - 0.5)*2*graphWidth,
        - py*graphHeight
    )
    fun proj(p: Map.Entry<CalibrationBin,CalibrationEntry>):List2<Double> = proj(p.key.mid, p.value.successRate ?: 0.0)
    fun pt(c: List2<Double>) = c.joinToString(" ")
    fun pt(x: Number, y: Number) = "$x $y"
    fun ptp(px: Double, py: Double) = pt(proj(px,py))
    val userLineColor = "#6319FF"
    svg {
        css(override=props) {
            width = 100.pct
            this.height = 100.pct//height.px
        }
        overflow = "visible"

        defs {
            ReactSVG.clipPath {
                id = "graph"
                rect {
                    x = leftPad
                    y = topPad
                    width = graphWidth
                    this.height = graphHeight
                }
            }
        }
        fun confidenceBand(range: ClosedFloatingPointRange<Double>, color: String) {
            val (l,h) = range
            path {
                d = "M ${ptp(0.5, 0.5+l)} L ${ptp(1.0, 1.0+l)} L ${ptp(1.0, 1.0+h)} L ${ptp(0.5, 0.5+h)}"
                clipPath = "url(#graph)"
                fill = color
                stroke ="none"
                strokeWidth= 0.0
            }
        }
        //val bands = listOf(
        //    -1.0,
        //    -slightMiscalibRadius,
        //    -wellCalibratedRadius,
        //    +wellCalibratedRadius,
        //    +slightMiscalibRadius,
        //    +1.0
        //).zip((170..320 step 30).reversed()) { a,b-> a to "hsl($b, 85%, 85%)" }
        calibrationBands.forEach { (range,color) -> confidenceBand(range,color) }
        path {
            // line of perfect calibration
            d = "M ${pt(proj(0.5, 0.5))} L ${pt(proj(1.0, 1.0))}"
            stroke = "#505050"
            strokeWidth = 2.0
            strokeDasharray = "2,3"
        }
        g {
            stroke = "#666"
            path {
                d = "M ${pt(origin)} L ${pt(proj(0.5, 1.0))}"
            }
            path {
                d = "M ${pt(origin)} L ${pt(proj(1.0, 0.0))}"
            }
            path {
                d = "M ${pt(proj(0.5, 1.0))} L ${pt(proj(1.0, 1.0))}"
            }
            path {
                d = "M ${pt(proj(1.0, 0.0))} L ${pt(proj(1.0, 1.0))}"
            }
        }
        g {
            stroke = "rgba(0,0,0,15%)"
            (1..9).map { it / 10.0 }.forEach {
                path { d = "M  ${pt(proj(0.5, it))} L  ${pt(proj(1.0, it))}" }
            }
            listOf(0.525, 0.6, 0.7, 0.8, 0.9, 0.975).forEach {
                path { d = "M  ${pt(proj(it,0.0))} L  ${pt(proj(it, 1.0))}" }

            }
        }
        val ent = props.calib.entries.filter { it.value.total > 0 }
        if (ent.size > 1)
            path {
                d = ent.mapIndexed { idx, ent -> (if (idx == 0) "M" else "L") + pt(proj(ent)) }.joinToString(" ")
                stroke = userLineColor
                strokeWidth = 4.0
                fill = "none"
            }
        ent.forEach { ent ->
            circle {
                val pt = proj(ent)
                cx = pt.e1
                cy = pt.e2
                r = 4.0
                fill = userLineColor
            }
        }
    }
    div {
        css {
            position = Position.absolute
            top = topPad.px
            left = leftPad.px
            fontWeight = integer(600)
            paddingLeft = 5.px
            paddingTop = 2.px
            fontSize = 16.px
            fontVariantCaps = FontVariantCaps.smallCaps
            color = Color("#53848c")
        }
        +"underconfident"
    }
    div {
        css {
            position = Position.absolute
            right = rightPad.px
            bottom = botPad.px
            fontWeight = integer(600)
            paddingRight = 5.px
            paddingBottom = 4.px
            fontSize = 16.px
            fontVariantCaps = FontVariantCaps.smallCaps
            color = Color("#685487")
        }
        +"overconfident"
    }
}, ClassName {
    width = 100.pct
    height = 100.pct
    position = Position.relative
})

val CalibrationGraph = FC<CalibrationGraphProps> { props->
    val legendCSS = ClassName {
        fontSize = 13.px
        color = Color("#666")
    }
    div {
        css(override=props) {
            display = Display.grid
            gridTemplateRows = "1fr auto".unsafeCast<GridTemplateRows>()
            gridTemplateColumns = "auto 1fr".unsafeCast<GridTemplateRows>()
            height = (props.height?:500.0).px
            marginTop = 20.px
            marginRight = 25.px
            marginLeft = 10.px
            marginBottom = 5.px
        }
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.stretch
                justifyContent = JustifyContent.stretch
                justifyItems = JustifyItems.stretch
                    gridRow = integer(1)
                    gridColumn = integer(2)
            }
            CalibrationGraphContent {
                +props.except("className")
                css {
                    width = 100.pct
                }
            }
        }
        div { // vertical axis
            css(legendCSS) {
                gridRow = integer(1)
                gridColumn = integer(1)
                position = Position.relative
                textAlign = TextAlign.right
                marginRight = 5.px
            }
            div { // for autosizing
                +"100%"
                css {
                    visibility= Visibility.hidden
                }
            }
            (0..10).map { it / 10.0 }.forEach {
                div {
                    css {
                        position = Position.absolute
                        top = (100*(1-it)).pct
                        left = 0.px
                        right = 0.px
                        transform = translatey((-50).pct)
                    }
                    +fmtp(it)
                }
            }
        }
        div { // horizontal axis
            css(legendCSS) {
                gridRow = integer(2)
                gridColumn = integer(2)
                position = Position.relative
                textAlign = TextAlign.right
                marginTop = 5.px
            }
            div { // for autosizing
                +"100%"
                css {
                    visibility= Visibility.hidden
                }
            }
            (5..10).map { it / 10.0 }.forEach {
                div {
                    css {
                        position = Position.absolute
                        left = (100*(2*(it-0.5))).pct
                        top = 0.px
                        transform = translatex((-50).pct)
                    }
                    +fmtp(it)
                }
            }
        }
    }
}