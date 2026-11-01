package tools.confido.web.components.questions.predictions

import tools.confido.web.components.ZoomIcon
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.ButtonUnstyled
import web.cssom.*
import web.dom.Node
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.web.utils.panzoom1d.PZState

external interface ZoomPillProps : PropsWithChildren {
    /** Shows [rangeLabel] and a reset ✕ while collapsed. */
    var zoomed: Boolean
    var rangeLabel: String?
    /** Shows [children] (presets, hints) in place of the range. */
    var expanded: Boolean
    /** Loupe and range label both route here. */
    var onToggle: (() -> Unit)?
    var onReset: (() -> Unit)?
    /** Focus left the pill's subtree. */
    var onBlurOutside: (() -> Unit)?
    /** Subtle variant for compact header rows: a bare grey loupe icon (like the
     *  header's other icons), with the range/content in a legend-style chip. */
    var compact: Boolean?
}

/**
 * Loupe button that grows into a pill: the visible range plus a reset ✕ while
 * zoomed, or caller-supplied content while expanded. Shared by the binary
 * slider and numeric graphs so zoom reads the same everywhere.
 */
val ZoomPill = FC<ZoomPillProps>("ZoomPill") { props ->
    val compact = props.compact == true
    val open = props.expanded || props.zoomed
    Stack {
        direction = FlexDirection.row
        tabIndex = 0
        onBlur = { ev ->
            // Focus moving to one of our own buttons is not leaving.
            if (ev.relatedTarget == null || !ev.currentTarget.contains(ev.relatedTarget as Node))
                props.onBlurOutside?.invoke()
        }
        css {
            alignItems = AlignItems.center
            position = Position.relative
            if (compact) {
                gap = 4.px
                outline = None.none
            } else {
                borderRadius = 5.px
                height = 30.px
                border = Border(1.px, LineStyle.solid, MainPalette.primary.color)
                background = NamedColor.white
            }
        }
        if (compact) {
            ButtonUnstyled {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                    color = if (props.expanded) MainPalette.primary.color else Color("#999999")
                    hover { color = MainPalette.primary.color }
                }
                ZoomIcon {
                    css { transform = scalex(-1); width = 14.px; height = 14.px }
                }
                onClick = { props.onToggle?.invoke() }
            }
        } else {
            GraphButton {
                ZoomIcon {
                    css { transform = scalex(-1) }
                }
                onClick = { props.onToggle?.invoke() }
            }
        }
        div {
            css {
                fontSize = if (compact) 11.px else 12.px
                color = if (compact) Color("#555555") else NamedColor.black
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                overflow = Overflow.hidden
                maxWidth = if (props.expanded) 420.px else if (props.zoomed) 200.px else 0.px
                transition = Transition(
                    property = PropertyName.maxWidth,
                    duration = 200.ms,
                    timingFunction = TransitionTimingFunction.easeInOut
                )
                if (compact) {
                    // The header's legend-chip look.
                    lineHeight = 14.px
                    fontFamily = sansSerif
                    borderRadius = 4.px
                    if (open) border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
                }
            }
            val itemPadding = if (compact) Padding(1.px, 6.px) else Padding(2.px, 5.px)
            if (props.expanded) {
                +props.children
            } else if (props.zoomed) {
                ButtonUnstyled {
                    css {
                        padding = itemPadding
                        whiteSpace = WhiteSpace.nowrap
                    }
                    onClick = { props.onToggle?.invoke() }
                    +(props.rangeLabel ?: "")
                }
                ButtonUnstyled {
                    title = "Show full range"
                    css {
                        padding = if (compact) Padding(1.px, 5.px, 1.px, 1.px) else Padding(2.px, 7.px, 2.px, 3.px)
                        color = Color("#999999")
                        hover { color = Color("#555555") }
                    }
                    onClick = { props.onReset?.invoke() }
                    +"✕"
                }
            }
        }
    }
}

/** Visible part of the content domain, side padding excluded. */
val PZState.visibleDomainRange get() =
    viewportToContent(leftPadVisible).coerceIn(params.contentDomain)..
        viewportToContent(params.viewportWidth - rightPadVisible).coerceIn(params.contentDomain)
