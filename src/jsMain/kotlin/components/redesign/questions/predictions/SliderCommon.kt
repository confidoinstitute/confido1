package components.redesign.questions.predictions

import browser.*
import components.redesign.basic.*
import csstype.*
import dom.events.*
import dom.html.*
import emotion.react.*
import hooks.combineRefs
import hooks.useDraggable
import hooks.useEventListener
import hooks.useRefEffect
import kotlinx.js.*
import react.*
import react.dom.html.*
import tools.confido.utils.toFixed
import utils.panzoom1d.PZState
import kotlin.math.*

external interface SliderTrackProps: Props {
    var zoomState: PZState
    var marks: List<Double>?
}

val centerOrigin : Transform = translate((-50).pct, (-50).pct)

val SliderTrack = FC<SliderTrackProps>("SliderTrack") { props->
    val zoomState = props.zoomState
    val marks = props.marks ?: emptyList()
    ReactHTML.div {
        css {
            height = 4.px
            backgroundColor = Color("#4c4c4c")
            borderRadius = 2.px
            position = Position.absolute
            top = 50.pct
            transform = translatey((-50).pct)
            zIndex = integer(1)
        }
        style = jso {
            left = (zoomState.leftPadVisible - 2).px
            right = (zoomState.rightPadVisible - 2).px
        }
    }
    marks.forEach {value->
        ReactHTML.div {
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
                left = zoomState.contentToViewport(value).px
            }
        }
    }
}
enum class ThumbKind(val signpostColor: String) {
    Left("#0066FF"),
    Center(MainPalette.center.color.toString()),
    Right("#0066FF"),
}

fun ThumbKind.svg(disabled: Boolean) =
    "/static/slider-${name.lowercase()}-${if (disabled) "inactive" else "active"}.svg"


external interface SliderThumbProps : Props {
    var containerElement: HTMLElement
    var formatSignpost: ((Double)->String)?
    var zoomState: PZState
    var pos: Double
    var onDrag: ((Double, Boolean)->Unit)?
    var onDragEnd: (()->Unit)?
    var onDragStart: (()->Unit)?
    var disabled: Boolean?
    var kind: ThumbKind
    var signpostEnabled: Boolean?
}
val SliderThumb = FC<SliderThumbProps>("SliderThumb") { props->
    val pos = props.pos
    val zoomState = props.zoomState
    val posPx = zoomState.contentToViewport(pos)
    val disabled = props.disabled ?: false
    var focused by useState(false)
    var dragFocused by useState(false)
    var pressed by useState(false)
    var pressOffset by useState(0.0)
    val signpostVisible = (focused  && !dragFocused) || pressed
    val kind = props.kind
    val svg = kind.svg(disabled)
    val thumbRef = useRef<HTMLDivElement>()

    val dragRE = useDraggable<HTMLDivElement>(props.containerElement,
        onDragStart = {
                            if (disabled) return@useDraggable
                            pressed=true
                            if (!focused) {
                                thumbRef.current?.focus()
                                dragFocused = true
                            }
                            props.onDragStart?.invoke()
                      },
        onDrag = { pos, isCommit ->
            if (disabled) return@useDraggable
            props.onDrag?.invoke(zoomState.viewportToContent(pos), isCommit)},
        onDragEnd = {pressed=false },
        onClick = {
            if (disabled) return@useDraggable
            thumbRef.current?.focus()
            dragFocused = false
        }
    )
    ReactHTML.div {
        css {
            position = Position.absolute
            width = 38.px
            height = 40.px
            top = 50.pct
            transform = centerOrigin
            backgroundImage = url(svg)
            backgroundPositionX = BackgroundPositionX.center
            zIndex = integer(if (kind == ThumbKind.Center) 5 else 4)
            "&:focus" {
                // some browsers have default styling for focused elements, undo it
                // (we signal focus by showing the signpost)
                border = None.none
                outline = None.none
            }
        }
        ref = combineRefs(thumbRef, dragRE)
        style = jso {
            left = posPx.px
        }
        tabIndex = 0 // make focusable
        onFocus = { focused = true }
        onBlur = { focused = false; dragFocused = false; }


        //onTouchStart = { event->
        //    event.preventDefault() // do not autogenerate mousedown event (https://stackoverflow.com/a/31210694)
        //}
    }
    if (props.signpostEnabled ?: true) {
        ReactHTML.div {// signpost stem
            css {
                position = Position.absolute
                height = 120.px
                width = 2.px
                transform = translatex((-50).pct)
                backgroundColor = Color(kind.signpostColor)
                bottom = 50.pct
                zIndex = integer(3)
                visibility = if (signpostVisible) Visibility.visible else Visibility.hidden
            }
            style = jso {
                left = posPx.px
            }
        }
        ReactHTML.div {
            css {
                position = Position.absolute
                backgroundColor = Color(kind.signpostColor)
                bottom = 132.px
                zIndex = integer(4)
                borderRadius = 5.px
                padding = Padding(4.px, 6.px)
                fontSize = 20.px
                lineHeight = 24.px
                fontFamily = sansSerif
                fontWeight = integer(700)
                color = NamedColor.white
                visibility = if (signpostVisible) Visibility.visible else Visibility.hidden
            }
            style = jso {
                transform = translatex(
                    if (posPx <= zoomState.params.viewportWidth / 2)
                        max((-50).pct, (-posPx).px)
                    else {
                        val rightSpace = zoomState.params.viewportWidth - posPx
                        min((-50).pct, "calc(${rightSpace}px - 100%)".unsafeCast<Length>())
                    }
                )
                left = posPx.px
            }
            +(props.formatSignpost ?: {it.toFixed(1)})(pos)
        }
    }
}
