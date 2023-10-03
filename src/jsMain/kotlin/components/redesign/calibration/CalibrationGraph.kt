package components.redesign.calibration

import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.elementSizeWrapper
import csstype.pct
import csstype.px
import emotion.react.css
import react.FC
import react.dom.svg.AlignmentBaseline
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.text
import tools.confido.calibration.CalibrationBin
import tools.confido.calibration.CalibrationEntry
import tools.confido.calibration.CalibrationVector
import tools.confido.utils.List2
import tools.confido.utils.`Z+`
import tools.confido.utils.toFixed

external interface CalibrationGraphProps: PropsWithElementSize {
    var calib: CalibrationVector
}
val CalibrationGraph = elementSizeWrapper(FC<CalibrationGraphProps> {props->
    val height = 500.0
    val leftPad = 60.0
    val botPad = 30.0
    val topPad = 20.0
    val rightPad = 20.0
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
    fun fmtp(p: Double) = (100*p).toFixed(1).trimEnd('0').trimEnd('.')+"%"
    svg {
        css {
            width = 100.pct
            this.height = height.px
        }
        path {
            // line of perfect calibration
            d = "M ${pt(proj(0.5, 0.5))} L ${pt(proj(1.0, 1.0))}"
            stroke = "gray"
            strokeDasharray = "2,3"
        }
        path {
            d = "M ${pt(origin)} L ${pt(proj(0.5, 1.0))}"
            stroke = "black"
        }
        path {
            d = "M ${pt(origin)} L ${pt(proj(1.0, 0.0))}"
            stroke = "black"
        }
        val ent = props.calib.entries.filter{ it.value.total > 0 }
        if (ent.size > 1)
        path {
            d = ent.mapIndexed{ idx,ent -> (if (idx==0) "M" else "L") + pt(proj(ent)) }.joinToString(" ")
            stroke = "blue"
            fill = "none"
        }
        ent.forEach { ent->
            circle {
                val pt = proj(ent)
                cx = pt.e1
                cy = pt.e2
                r = 5.0
                fill = "blue"
            }
        }
        listOf(0.5, 0.6, 0.7, 0.8, 0.9, 1.0).forEach {
            text {
                +fmtp(it)
                val pt = proj(it, 0.0)
                x = pt.e1
                y = pt.e2 + 20.0
                textAnchor = "middle"
            }
        }
        (0..10).map{it / 10.0}.forEach {
            text {
                +fmtp(it)
                val pt = proj(0.5, it)
                x = pt.e1 - 5.0
                y = pt.e2
                textAnchor = "end"
                alignmentBaseline = AlignmentBaseline.middle
            }
        }
    }
})