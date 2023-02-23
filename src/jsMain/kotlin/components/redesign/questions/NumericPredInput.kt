package components.redesign.questions

import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.Stack
import components.redesign.basic.elementSizeWrapper
import csstype.*
import emotion.css.ClassName
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useState
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.utils.toFixed


external interface NumericPredInputProps : Props {
    var space: NumericSpace
    var dist: ContinuousProbabilityDistribution?
}

external interface NumericPredSliderProps : NumericPredInputProps, PropsWithElementSize {
    var zoomParams: ZoomParams?
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
            left = (zoomMgr.leftPadVisible - 2).px
            right = (zoomMgr.rightPadVisible - 2).px
            top = 50.pct
            transform = translatey((-50).pct)
            zIndex = integer(1)
        }
    }
    zoomMgr.marks.forEach {value->
        div {
            css {
                position = Position.absolute
                top = 50.pct
                left = zoomMgr.space2canvasCssPx(value).px
                width = 2.px
                height = 2.px
                backgroundColor = NamedColor.white
                borderRadius = 1.px
                zIndex = integer(2)
                transform = centerOrigin
            }
        }
    }
}
private enum class ThumbKind(val color: String) {
    Left("#0066FF"),
    Center("#00CC2E"),
    Right("#0066FF"),
}
private external interface NumericPredSliderThumbProps: NumericPredSliderInternalProps {
    var kind: ThumbKind
    var pos: Double
    var onChange: ((Double)->Unit)?
    var onCommit: ((Double)->Unit)?
    var disabled: Boolean?
}
private val NumericPredSliderThumb = FC<NumericPredSliderThumbProps> {props->
    val kind = props.kind
    val pos = props.pos
    val zoomMgr = props.zoomManager
    val posPx = zoomMgr.space2canvasCssPx(pos)
    val disabled = props.disabled ?: false
    var focused by useState(false)
    var pressed by useState(false)
    val signpostVisible = focused || pressed
    val svg = "/static/slider-${kind.name.lowercase()}-${if (disabled) "inactive" else "active"}.svg"
    div {
        css {
            position = Position.absolute
            width = 38.px
            height = 40.px
            top = 50.pct
            left = posPx.px
            transform = centerOrigin
            backgroundImage = url(svg)
            backgroundPositionX = BackgroundPositionX.center
            zIndex = integer(if (kind == ThumbKind.Center) 5 else 4)
            "&:focus" {
                border = None.none
            }
        }
        tabIndex = 0 // make focusable
        onFocus = { focused = true }
        onBlur = { focused = false }
        onMouseDown = {

        }
    }
    if (signpostVisible) {
        div {// signpost stem
            css {
                position = Position.absolute
                height = 112.px
                width = 2.px
                transform = translatex((-50).pct)
                backgroundColor = Color(kind.color)
                left = posPx.px
                bottom = 50.pct
                zIndex = integer(3)
            }
        }
        div {
            css {
                position = Position.absolute
                transform = translatex((-50).pct)
                backgroundColor = Color(kind.color)
                left = posPx.px
                bottom = 132.px
                zIndex = integer(4)
                borderRadius = 5.px
                padding = Padding(4.px,6.px)
                fontSize = 20.px
                lineHeight = 24.px
                fontFamily = FontFamily.sansSerif
                fontWeight = integer(700)
                color = NamedColor.white
            }
            +pos.toFixed(2)
        }
    }
}


val NumericPredSlider = elementSizeWrapper(FC<NumericPredSliderProps> { props->
    val dist = props.dist
    div {
        css {
            height = 40.px
            minHeight = 40.px
            flexShrink = number(0.0)
            position = Position.relative
            // overflowX = Overflow.hidden // FIXME apparently, this does not work
            // overflowY = Overflow.visible
        }
        val zoomManager = SpaceZoomManager(props.space, props.elementWidth, props.zoomParams ?: ZoomParams())
        NumericPredSliderTrack {
            +props
            this.zoomManager = zoomManager
        }
        if (dist != null && dist is TruncatedNormalDistribution) {
            val ci = dist.confidenceInterval(0.8)
            NumericPredSliderThumb{
                +props
                this.zoomManager = zoomManager
                kind = ThumbKind.Left
                pos = ci.start
            }
            NumericPredSliderThumb{
                +props
                this.zoomManager = zoomManager
                kind = ThumbKind.Center
                pos = dist.pseudoMean
            }
            NumericPredSliderThumb{
                +props
                this.zoomManager = zoomManager
                kind = ThumbKind.Right
                pos = ci.endInclusive
            }
        }
    }
}, ClassName {
    // overflowX = Overflow.hidden // FIXME apparently, this does not work
    // overflowY = Overflow.visible
})

val NumericPredInput = FC<NumericPredInputProps> { props->
    var zoomParams by useState(ZoomParams())
    Stack {
        css {
            overflowX = Overflow.hidden
        }
        NumericPredGraph {
            space = props.space
            dist = props.dist
            onZoomChange = { zoomParams = it }
        }
        NumericPredSlider {
            +props
            this.zoomParams = zoomParams
        }
    }
}