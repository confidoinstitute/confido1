package components.redesign.questions

import browser.window
import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.Stack
import components.redesign.basic.elementSizeWrapper
import csstype.*
import dom.events.TouchEvent
import dom.html.HTMLDivElement
import dom.html.HTMLElement
import emotion.css.ClassName
import emotion.react.css
import kotlinx.js.asList
import kotlinx.js.jso
import react.*
import react.dom.events.EventHandler
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.div
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.utils.mid
import tools.confido.utils.size
import tools.confido.utils.toFixed
import utils.breakLines
import kotlin.math.abs


external interface NumericPredInputProps : Props {
    var space: NumericSpace
    var dist: ContinuousProbabilityDistribution?
}

external interface NumericPredSliderProps : NumericPredInputProps, PropsWithElementSize {
    var zoomParams: ZoomParams?
    var onChange: ((TruncatedNormalDistribution) -> Unit)?
    var onCommit: ((TruncatedNormalDistribution) -> Unit)?
}

external interface NumericPredSliderInternalProps : NumericPredSliderProps {
    var zoomManager: SpaceZoomManager
}

val centerOrigin : Transform = translate((-50).pct, (-50).pct)

private val NumericPredSliderTrack = FC<NumericPredSliderInternalProps> {props->
    val zoomMgr = props.zoomManager
    div {
        css {
            height = 4.px
            // FIXME: This if from figma. Does it make sense to have alpha here or should it just be gray?
            backgroundColor = Color("#4c4c4c")
            borderRadius = 2.px
            position = Position.absolute
            top = 50.pct
            transform = translatey((-50).pct)
            zIndex = integer(1)
        }
        style = jso {
            left = (zoomMgr.leftPadVisible - 2).px
            right = (zoomMgr.rightPadVisible - 2).px
        }
    }
    zoomMgr.marks.forEach {value->
        div {
            css {
                position = Position.absolute
                top = 50.pct
                width = 2.px
                height = 2.px
                backgroundColor = NamedColor.white
                borderRadius = 1.px
                zIndex = integer(2)
                transform = centerOrigin
            }

            style = jso {
                left = zoomMgr.space2canvasCssPx(value).px
            }
        }
    }
}
fun binarySearch(initialRange: ClosedFloatingPointRange<Double>, desiredValue: Double, maxSteps: Int, f: (Double) -> Double): ClosedFloatingPointRange<Double> {
    var curRange = initialRange
    fun cmp(x: Double) = desiredValue.compareTo(f(x))
    for (step in 1..maxSteps) {
        if (cmp(curRange.endInclusive) == 1) curRange = curRange.start .. (2*curRange.endInclusive)
        else break
    }
    for (step in 1..maxSteps) {
        val mid = curRange.mid
        when (cmp(mid)) {
            0 -> return mid..mid
            1 -> curRange = mid..curRange.endInclusive // want higher
            -1 -> curRange = curRange.start..mid // want lower
        }
    }
    return curRange
}
fun findDistribution(space: NumericSpace, center: Double, ciWidth: Double): TruncatedNormalDistribution {
    val pseudoStdev = binarySearch(0.0..ciWidth, ciWidth, 30) {
        TruncatedNormalDistribution(space, center, it).confidenceInterval(0.8).size
    }.mid
    return TruncatedNormalDistribution(space, center, pseudoStdev)
}

val NumericPredSlider = elementSizeWrapper(FC<NumericPredSliderProps> { props->
    val space = props.space
    val propDist = props.dist as? TruncatedNormalDistribution
    var center by useState(propDist?.pseudoMean)
    var ciWidth by useState(propDist?.confidenceInterval(0.8)?.size)
    val ciRadius = ciWidth?.let { it / 2.0 }
    val zoomManager = SpaceZoomManager(props.space, props.elementWidth, props.zoomParams ?: ZoomParams())
    val minCIRadius = 20.0 / zoomManager.xScale // do not allow the thumbs to overlap too much
    val ci = if (center != null && ciWidth != null) {
        if (center!! + ciRadius!! > space.max) (space.max - ciWidth!!)..space.max
        else if (center!! - ciRadius < space.min) space.min..(space.min + ciWidth!!)
        else (center!! - ciRadius)..(center!! + ciRadius)
    } else null
    useEffect(propDist) {
        propDist?.let {
            center = propDist.pseudoMean
            ciWidth = propDist.confidenceInterval(0.8).size
        }
    }
    val dist = useMemo(space, center, ciWidth) {
        if (center != null && ciWidth != null) findDistribution(space, center!!, ciWidth!!)
        else null
    }
    useEffect(dist?.pseudoMean, dist?.pseudoStdev) {
        dist?.let { props.onChange?.invoke(dist) }
    }
    div {
        css {
            height = 40.px
            minHeight = 40.px
            flexShrink = number(0.0)
            position = Position.relative
            // overflowX = Overflow.hidden // FIXME apparently, this does not work
            // overflowY = Overflow.visible
        }
        NumericPredSliderTrack {
            +props
            this.zoomManager = zoomManager
        }
        if (dist != null) {
            SliderThumb{
                key = "thumb_left"
                this.containerElement = props.element
                this.zoomManager = zoomManager
                kind = ThumbKind.Left
                pos = ci!!.start
                onThumbChange = {
                    center?.let { center->
                        val effectivePos = minOf(it, center - minCIRadius)
                        val naturalRadius = center - effectivePos
                        if (center + naturalRadius > space.max) {
                            ciWidth = space.max - effectivePos
                        } else {
                            ciWidth = 2 * naturalRadius
                        }
                    }
                }
            }
            SliderThumb{
                key = "thumb_center"
                this.containerElement = props.element
                this.zoomManager = zoomManager
                kind = ThumbKind.Center
                pos = dist.pseudoMean
                onThumbChange = { center = it }
            }
            SliderThumb{
                key = "thumb_right"
                this.containerElement = props.element
                this.zoomManager = zoomManager
                kind = ThumbKind.Right
                pos = ci!!.endInclusive
                onThumbChange = {
                    center?.let { center->
                        val effectivePos = maxOf(it, center + minCIRadius)
                        val naturalRadius = effectivePos - center
                        if (center - naturalRadius < space.min) {
                            ciWidth = effectivePos - space.min
                        } else {
                            ciWidth = 2 * naturalRadius
                        }
                    }
                }
            }
        }
    }
}, ClassName {
    // overflowX = Overflow.hidden // FIXME apparently, this does not work
    // overflowY = Overflow.visible
})


val NumericPredInput = FC<NumericPredInputProps> { props->
    var zoomParams by useState(ZoomParams())
    val propDist = props.dist as? TruncatedNormalDistribution?
    var previewDist by useState(propDist)
    useEffect(propDist) { previewDist = propDist }
    Stack {
        css {
            overflowX = Overflow.hidden
        }
        NumericPredGraph {
            this.space = props.space
            this.dist = previewDist
            onZoomChange = { zoomParams = it }
        }
        NumericPredSlider {
            this.space = props.space
            this.zoomParams = zoomParams
            this.dist = props.dist
            this.onChange = {
                previewDist = it
            }
        }

    }
}