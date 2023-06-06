package components.redesign.questions.predictions

import browser.window
import csstype.*
import dom.html.HTMLDivElement
import emotion.react.css
import hooks.ElementSize
import hooks.useElementSize
import kotlinx.js.jso
import react.*
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.svg
import tools.confido.utils.intersects
import utils.addEventListener
import utils.removeEventListener
import web.events.Event

data class PredictionFlag(
    val color: Color,
    val content: ReactNode,
    val flagpole: Boolean = true,
)

enum class PredictionFlagAnchor {
    LEFT,
    RIGHT;

    fun position(pos: Double, screenWidth: Double) = when(this) {
        LEFT -> pos
        RIGHT -> screenWidth - pos
    }
}

enum class PredictionFlagMode {
    NORMAL,
    BORDER,
    FULLWIDTH;

    fun span(pos: Double, width: Double, screenWidth: Double, anchor: PredictionFlagAnchor) = when(this) {
        NORMAL -> when(anchor) {
            PredictionFlagAnchor.LEFT -> pos - 1 .. pos + width - 1
            PredictionFlagAnchor.RIGHT -> pos - width + 1 .. pos + 1
        }
        BORDER -> when(anchor) {
            PredictionFlagAnchor.LEFT -> screenWidth - width .. screenWidth
            PredictionFlagAnchor.RIGHT -> 0.0 .. width
        }
        FULLWIDTH -> 0.0 .. screenWidth
    }
}

external interface PredictionFlagContentProps : Props {
    var flag: PredictionFlag
    var size: ElementSize<HTMLDivElement>
    var position: FlagPosition?
    /** Hide when not hovered. Defaults to false. */
    var collapseUntilHovered: Boolean?
}

fun CSSProperties.flagContentPosition(position: FlagPosition) {
    when(position.mode) {
        PredictionFlagMode.NORMAL -> when(position.anchor) {
            PredictionFlagAnchor.LEFT -> {
                left = (position.positionX - 1).px
                borderBottomLeftRadius = 0.px
            }
            PredictionFlagAnchor.RIGHT -> {
                right = (position.positionX - 1).px
                borderBottomRightRadius = 0.px
            }
        }
        PredictionFlagMode.BORDER -> when(position.anchor) {
            PredictionFlagAnchor.LEFT -> {
                right = (-1).px
                marginLeft = (-1).px
            }
            PredictionFlagAnchor.RIGHT -> {
                left = (-1).px
                marginRight = (-1).px
            }
        }
        PredictionFlagMode.FULLWIDTH -> {
            left = (-1).px
            marginRight = (-1).px
        }
    }
    bottom = position.positionY.px
}

val PredictionFlagContent = FC<PredictionFlagContentProps> { props ->
    div {
        key = "flag-size-ref"
        ref = props.size.ref
        css {
            position = Position.absolute
            top = 0.px
            left = (-2).px
            visibility = Visibility.hidden
        }
        div {
            css {
                padding = Padding(5.px, 8.px)
            }
            +props.flag.content
        }
    }

    val pos = props.position ?: return@FC
    val positionX = pos.positionX

    val collapseUntilHovered = props.collapseUntilHovered ?: false
    var hidden by useState(collapseUntilHovered)
    val background = useRef<HTMLDivElement>()

    useEffect {
        // This must not be a fun, as referencing it through ::onMouseMove would result
        // in a different function being created in addEventListener and removeEventListener,
        // and removal would fail.
        val onMouseMove = { e: Event ->
            e as dom.events.MouseEvent
            val target = background.current as HTMLDivElement
            val boundingRect = target.getBoundingClientRect()
            val x = when (pos.anchor) {
                PredictionFlagAnchor.LEFT -> e.clientX - boundingRect.left - 1
                PredictionFlagAnchor.RIGHT -> boundingRect.right - e.clientX - 1
            }

            val distance = 20
            val isMouseNearPole = x in (pos.positionX - props.size.width - distance)..(pos.positionX + props.size.width + distance)
            if (collapseUntilHovered) {
                hidden = !isMouseNearPole
            }
        }

        window.addEventListener("mousemove", onMouseMove);
        cleanup {
            window.removeEventListener("mousemove", onMouseMove);
        }
    }

    div {
        key = "background"
        ref = background
        css {
            opacity = number(0.7)
            zIndex = integer(pos.zIndex)
            position = Position.absolute
            width = 100.pct
            height = 100.pct
        }
        if (props.flag.flagpole) {
            div {
                key = "flagpole"
                style = jso {
                    when (pos.anchor) {
                        PredictionFlagAnchor.LEFT -> left = (positionX - 1).px
                        PredictionFlagAnchor.RIGHT -> right = (positionX - 1).px
                    }
                    height = (pos.positionY + 10).px
                }
                css {
                    position = Position.absolute
                    bottom = 0.px
                    backgroundColor = props.flag.color
                    width = 2.px
                }
            }
            div {
                key = "flagpole-bottom"
                style = jso {
                    when (pos.anchor) {
                        PredictionFlagAnchor.LEFT -> left = (positionX - 4).px
                        PredictionFlagAnchor.RIGHT -> right = (positionX - 4).px
                    }
                }
                css {
                    position = Position.absolute
                    bottom = (-4).px
                    width = 8.px
                    height = 8.px
                    borderRadius = 100.pct
                    backgroundColor = props.flag.color
                }
            }
            if (pos.mode == PredictionFlagMode.NORMAL)
                svg {
                    key = "flagpole-top"
                    width = 3.0
                    height = 3.0
                    fill = props.flag.color.toString()
                    style = jso {
                        bottom = (pos.positionY - 3).px
                        when (pos.anchor) {
                            PredictionFlagAnchor.LEFT -> left = (positionX + 1).px
                            PredictionFlagAnchor.RIGHT -> right = (positionX + 1).px
                        }
                    }
                    css {
                        position = Position.absolute
                    }
                    ReactSVG.path {
                        d = when (pos.anchor) {
                            PredictionFlagAnchor.LEFT -> "M 0 0 L 0 3 A 3 3 0 0 1 3 0 L 0 0 z"
                            PredictionFlagAnchor.RIGHT -> "M 0 0 A 3 3 0 0 1 3 3 L 3 0 L 0 0 z"
                        }
                    }
                }
        }
        div {
            key = "flag-back"
            css {
                position = Position.absolute
                borderRadius = 10.px
                backgroundColor = props.flag.color
                width = props.size.width.px
                height = props.size.height.px
                if (collapseUntilHovered) {
                    transition = Transition(ident("width"), 0.2.s, TransitionTimingFunction.easeInOut)
                }
                if (hidden) {
                    width = 2.px
                }
            }
            style = jso {
                flagContentPosition(pos)
            }
        }
    }

    div {
        key = "flag"
        css {
            position = Position.absolute
            padding = Padding(5.px, 8.px)
            zIndex = integer(pos.zIndex)
        }
        style = jso {
            flagContentPosition(pos)
        }
        if (collapseUntilHovered) {
            div {
                css {
                    position = Position.relative
                    overflow = Overflow.hidden
                    transition = Transition(ident("width"), 0.2.s, TransitionTimingFunction.easeInOut)
                    if (hidden) {
                        width = 0.px
                    }
                }
                +props.flag.content
            }
        } else {
            +props.flag.content
        }
    }
}

external interface PredictionFlagsProps : Props {
    var flags: List<PredictionFlag>
    var flagPositions: List<Double>
    /** Hide when not hovered. Defaults to false. */
    var collapseUntilHovered: Boolean?
}

data class FlagPosition(
    val anchor: PredictionFlagAnchor,
    val mode: PredictionFlagMode,
    val positionX: Double,
    val positionY: Double,
    val zIndex: Int = 0,
)

fun flagAnchor(pos: Double, width: Double, screenWidth: Double, preferredAnchor: PredictionFlagAnchor) =
    if (width * 2 <= screenWidth) {
        when(preferredAnchor) {
            PredictionFlagAnchor.LEFT ->
                if (pos + width - 1 <= screenWidth)
                    Pair(PredictionFlagAnchor.LEFT, PredictionFlagMode.NORMAL)
                else
                    Pair(PredictionFlagAnchor.RIGHT, PredictionFlagMode.NORMAL)
            PredictionFlagAnchor.RIGHT ->
                if (pos - width + 1 >= 0)
                    Pair(PredictionFlagAnchor.RIGHT, PredictionFlagMode.NORMAL)
                else
                    Pair(PredictionFlagAnchor.LEFT, PredictionFlagMode.NORMAL)
        }
    } else if (width < screenWidth) {
        when(preferredAnchor) {
            PredictionFlagAnchor.LEFT ->
                if (pos + width - 2 <= screenWidth)
                    Pair(PredictionFlagAnchor.LEFT, PredictionFlagMode.NORMAL)
                else
                    Pair(PredictionFlagAnchor.LEFT, PredictionFlagMode.BORDER)
            PredictionFlagAnchor.RIGHT ->
                if (pos - width + 2 >= 0)
                    Pair(PredictionFlagAnchor.RIGHT, PredictionFlagMode.NORMAL)
                else
                    Pair(PredictionFlagAnchor.RIGHT, PredictionFlagMode.BORDER)
        }
    } else Pair(if (pos <= screenWidth/2) PredictionFlagAnchor.LEFT else PredictionFlagAnchor.RIGHT, PredictionFlagMode.FULLWIDTH)

// WORKS ONLY WITH SIZE MAX 2
val PredictionFlags = FC<PredictionFlagsProps> {props ->
    if (props.flags.isEmpty()) return@FC
    if (props.flags.size > 2) return@FC

    val flagsWithPosition = props.flags.zip(props.flagPositions)
    val orderedFlags = useMemo(flagsWithPosition) { flagsWithPosition.sortedBy { it.second } }

    val outerSize = useElementSize<HTMLDivElement>()
    val size1 = useElementSize<HTMLDivElement>()
    val size2 = useElementSize<HTMLDivElement>()

    var position1 by useState<FlagPosition?>(null)
    var position2 by useState<FlagPosition?>(null)

    useLayoutEffect(props.flagPositions, outerSize.width, outerSize.height, size1.width, size1.height, size2.width, size2.height) {
        if (orderedFlags.size == 1) {
            val pos = orderedFlags[0].second
            if (size1.width > 0) {
                val (anchor, mode) = flagAnchor(pos, size1.width, outerSize.width, if (pos <= outerSize.width / 2) PredictionFlagAnchor.LEFT else PredictionFlagAnchor.RIGHT)
                position1 = FlagPosition(
                    anchor = anchor,
                    mode = mode,
                    positionX = anchor.position(pos, outerSize.width),
                    positionY = kotlin.math.min(outerSize.height / 2, outerSize.height - size1.height))
                position2 = null
            }
        } else if (orderedFlags.size == 2) {
            val pos1 = orderedFlags[0].second
            val pos2 = orderedFlags[1].second
            val (anchor1, mode1) = flagAnchor(pos1, size1.width, outerSize.width, PredictionFlagAnchor.RIGHT)
            val (anchor2, mode2) = flagAnchor(pos2, size2.width, outerSize.width, PredictionFlagAnchor.LEFT)

            val range1 = mode1.span(pos1, size1.width, outerSize.width, anchor1)
            val range2 = mode2.span(pos2, size2.width, outerSize.width, anchor2)

            var y1 = kotlin.math.min(outerSize.height / 2, outerSize.height - size1.height)
            var y2 = kotlin.math.min(outerSize.height / 2, outerSize.height - size2.height)

            var z1 = 0
            var z2 = 0

            if (range1.intersects(range2)) { // Overlapping
                // Move bottom by half its height down, move up by half bottom's height up
                // Move the flag that would be covered if it were on top to the bottom
                if ((pos1-1..pos1+1).intersects(range2)) {
                    val bottom = y1 - size1.height / 2 - 1
                    val top = y2 + size1.height / 2 + 1
                    y2 = kotlin.math.min(top, outerSize.height - size2.height)
                    y1 = kotlin.math.max(bottom, 0.0)
                    z1 = 1
                } else {
                    val bottom = y2 - size2.height / 2 - 1
                    val top = y1 + size2.height / 2 + 1
                    y1 = kotlin.math.min(top, outerSize.height - size1.height)
                    y2 = kotlin.math.max(bottom, 0.0)
                    z2 = 1
                }
            }

            position1 = FlagPosition(
                anchor = anchor1,
                mode = mode1,
                positionX = if (anchor1 == PredictionFlagAnchor.RIGHT) outerSize.width - pos1 else pos1,
                positionY = y1,
                zIndex = z1
            )
            position2 = FlagPosition(
                anchor = anchor2,
                mode = mode2,
                positionX = if (anchor2 == PredictionFlagAnchor.RIGHT) outerSize.width - pos2 else pos2,
                positionY = y2,
                zIndex = z2
            )
        }
    }

    div {
        ref = outerSize.ref
        css {
            zIndex = integer(10)
            position = Position.absolute
            top = 0.px
            left = 0.px
            width = 100.pct
            height = 100.pct
        }
        div {
            PredictionFlagContent {
                flag = orderedFlags[0].first
                size = size1
                position = position1
                collapseUntilHovered = props.collapseUntilHovered
            }
            if (orderedFlags.size > 1)
                PredictionFlagContent {
                    flag = orderedFlags[1].first
                    size = size2
                    position = position2
                    collapseUntilHovered = props.collapseUntilHovered
                }
        }
    }
}
