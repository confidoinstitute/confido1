package components.redesign.calibration

import components.redesign.basic.*
import csstype.*
import emotion.react.css
import emotion.css.ClassName
import payloads.requests.CalibrationWho
import react.FC
import react.PropsWithClassName
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.defs
import react.dom.svg.ReactSVG.g
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.rect
import react.dom.svg.ReactSVG.svg
import tools.confido.calibration.CalibrationBin
import tools.confido.calibration.CalibrationEntry
import tools.confido.calibration.CalibrationVector
import tools.confido.utils.*
import utils.except

external interface CalibrationGraphProps: PropsWithElementSize, PropsWithClassName {
    var calib: CalibrationVector
    var height: Double?
    var who: CalibrationWho?
    var areaLabels: Boolean?
}

val wellCalibratedRadius = 0.05
val slightMiscalibRadius = 0.2
private fun fmtp(p: Double) = (100*p).toFixed(1).trimEnd('0').trimEnd('.')+"%"

data class CalibrationBand(
    val range: ClosedFloatingPointRange<Double>,
    val color: String,
    val name: String,
    val sign: String,
) {
    fun absRange(center: Double): ClosedFloatingPointRange<Double>? {
        val r = range.endpoints.map{ (it + center).clamp01() }.toRange()
        return if (r.size == 0.0) null else r
    }

    fun absRange(bin: CalibrationBin) = absRange(bin.mid)
}

//val calibrationBands = listOf(
//    CalibrationBand(-1.0..-slightMiscalibRadius, "#bc97f4", "Overconfident", ">"),
//    CalibrationBand(-slightMiscalibRadius..-wellCalibratedRadius, "#d0b7f5", "Slightly overconfident", ">"),
//    CalibrationBand(-wellCalibratedRadius..wellCalibratedRadius, "#f5fafa", "Well-calibrated", "≈"),
//    CalibrationBand(wellCalibratedRadius..slightMiscalibRadius, "#b1ebf3", "Slightly overconfident", "<"),
//    CalibrationBand(slightMiscalibRadius..1.0, "#90e4f3", "Underconfident", "<"),
//)
val calibrationBands = listOf(
    CalibrationBand(-1.0..-slightMiscalibRadius, "#b08bff", "Overconfident", ">"),
    CalibrationBand(-slightMiscalibRadius..-wellCalibratedRadius, "#d4bfff", "Slightly overconfident", ">"),
    CalibrationBand(-wellCalibratedRadius..wellCalibratedRadius, "#f5fafa", "Well-calibrated", "≈"),
    CalibrationBand(wellCalibratedRadius..slightMiscalibRadius, "#bffbff", "Slightly overconfident", "<"),
    CalibrationBand(slightMiscalibRadius..1.0, "#7ff7ff", "Underconfident", "<"),
)



val CalibrationGraphContent = elementSizeWrapper(FC<CalibrationGraphProps> { props->
    val height = props.elementHeight
    val graphWidth = props.elementWidth
    val graphHeight = height
    val origin = List2(0.0, height)
    fun proj(px: Double, py: Double) = origin `Z+` List2(
        (px - 0.5)*2*graphWidth,
        - py*graphHeight
    )
    fun proj(p: Map.Entry<CalibrationBin,CalibrationEntry>):List2<Double> = proj(p.key.mid, p.value.successRate ?: 0.0)
    fun pt(c: List2<Double>) = c.joinToString(" ")
    fun pt(x: Number, y: Number) = "$x $y"
    fun ptp(px: Double, py: Double) = pt(proj(px,py))
    val userLineColor = "#6319FF"
    val entries = props.calib.entries.filter { it.value.total > 0 }
    svg {
        css(override=props) {
            width = 100.pct
            this.height = 100.pct//height.px
            if (entries.size == 0) {
                filter = "saturate(35%) contrast(60%) brightness(125%)".unsafeCast<Filter>()
            }
        }
        overflow = "visible"

        defs {
            ReactSVG.clipPath {
                id = "graph"
                rect {
                    x = 0.0
                    y = 0.0
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
        //g {
        //    stroke = "#666"
        //    path {
        //        d = "M ${pt(origin)} L ${pt(proj(0.5, 1.0))}"
        //    }
        //    path {
        //        d = "M ${pt(origin)} L ${pt(proj(1.0, 0.0))}"
        //    }
        //    path {
        //        d = "M ${pt(proj(0.5, 1.0))} L ${pt(proj(1.0, 1.0))}"
        //    }
        //    path {
        //        d = "M ${pt(proj(1.0, 0.0))} L ${pt(proj(1.0, 1.0))}"
        //    }
        //}
        g {
            stroke = "rgba(0,0,0,15%)"
            (1..9).map { it / 10.0 }.forEach {
                path { d = "M  ${pt(proj(0.5, it))} L  ${pt(proj(1.0, it))}" }
            }
            listOf(0.525, 0.6, 0.7, 0.8, 0.9, 0.975).forEach {
                path { d = "M  ${pt(proj(it,0.0))} L  ${pt(proj(it, 1.0))}" }

            }
        }
        if (entries.size > 1)
            path {
                d = entries.mapIndexed { idx, ent -> (if (idx == 0) "M" else "L") + pt(proj(ent)) }.joinToString(" ")
                stroke = userLineColor
                strokeWidth = 4.0
                fill = "none"
            }
        entries.forEach { ent ->
            circle {
                val pt = proj(ent)
                cx = pt.e1
                cy = pt.e2
                r = 4.0
                fill = userLineColor
            }
        }
    }
    if ((props.areaLabels ?: true) && entries.size > 0) {
        div {
            css {
                position = Position.absolute
                top = 0.px
                left = 0.px
                fontWeight = integer(600)
                paddingLeft = 5.px
                paddingTop = 2.px
                fontSize = 16.px
                fontVariantCaps = FontVariantCaps.smallCaps
                color = Color("#46888c")
            }
            +"underconfident"
        }
        div {
            css {
                position = Position.absolute
                right = 0.px
                bottom = 0.px
                fontWeight = integer(600)
                paddingRight = 5.px
                paddingBottom = 4.px
                fontSize = 16.px
                fontVariantCaps = FontVariantCaps.smallCaps
                color = Color("#5a4782")
            }
            +"overconfident"
        }
    }
    if (entries.size == 0) {
        div {
            css {
                position = Position.absolute
                left = 50.pct
                top = 50.pct
                transform = translate((-50).pct, (-50).pct)
                fontSize = 20.px
                fontWeight = integer(500)
                color = Color("#222")
            }
            +"No data yet"
        }
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
    val axisLabelCSS = ClassName {
        fontSize = 13.px
        color = Color("#666")
        fontWeight = integer(600)
        textAlign = TextAlign.center
    }
    div {
        css(override=props) {
            display = Display.grid
            gridTemplateRows = "1fr auto auto".unsafeCast<GridTemplateRows>()
            gridTemplateColumns = "auto auto 1fr".unsafeCast<GridTemplateRows>()
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
                gridColumn = integer(3)
                border = Border(1.px, LineStyle.solid, Color("#666"))
            }
            CalibrationGraphContent {
                +props.except("className")
                css {
                    width = 100.pct
                }
            }
        }
        div {
            css(axisLabelCSS) {
                gridRow = integer(1)
                gridColumn = integer(1)
                writingMode = WritingMode.verticalLr
                transform = rotate(180.deg)
            }
            +props.who.withAdjective("accuracy").capFirst()
        }
        div { // vertical axis
            css(legendCSS) {
                gridRow = integer(1)
                gridColumn = integer(2)
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
                gridColumn = integer(3)
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
        div {
            css(axisLabelCSS) {
                gridRow = integer(3)
                gridColumn = integer(3)
            }
            +props.who.withAdjective("confidence").capFirst()
        }
    }
}