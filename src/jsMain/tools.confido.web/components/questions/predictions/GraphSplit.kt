package tools.confido.web.components.questions.predictions

import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.spaces.NumericSpace
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import web.cssom.*

/** Fill colors of a hovered numeric graph's below/above halves; deliberately
 *  unlike the 80% CI pair and the orange group-estimate flag. */
val splitBelowColor = Color("#f59fc8")
val splitAboveColor = Color("#6fa8ff")

/** A numeric graph's hover split: P(answer < [x]) under its distribution.
 *  [label] is [x] formatted exactly as the graph's own readout shows it.
 *  Comparison charts also set [belowBefore], the older state's P(answer < [x]),
 *  with [below] then being the newer one's. */
data class GraphSplit(
    val x: Double,
    val label: String,
    val below: Double,
    val belowBefore: Double? = null,
)

/** Formats [x] to about the precision one pixel resolves at [unitsPerPx],
 *  capped by the space's own [NumericSpace.decimals]. */
fun NumericSpace.formatAtResolution(x: Double, unitsPerPx: Double): String {
    if (representsDays || !(unitsPerPx > 0.0)) return formatValue(x, true, false)
    val pxDecimals = -floor(log10(unitsPerPx)).toInt()
    val step = 10.0.pow(-pxDecimals.coerceAtMost(0))
    return copy(decimals = pxDecimals.coerceIn(0, decimals)).formatValue(round(x / step) * step, true, false)
}

/**
 * Carries a graph's [GraphSplit] to a sibling description. A plain observable
 * rather than state in a common parent: the split changes on every mouse move,
 * and this way only the subscribers re-render, not everything in between.
 */
class GraphSplitChannel {
    var value: GraphSplit? = null
        private set
    private val listeners = mutableSetOf<(GraphSplit?) -> Unit>()

    fun publish(split: GraphSplit?) {
        if (split == value) return
        value = split
        listeners.toList().forEach { it(split) }
    }

    fun subscribe(listener: (GraphSplit?) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}

val GraphSplitContext = createContext<GraphSplitChannel?>(null)

/** Gives its subtree a fresh [GraphSplitChannel] (owned here, so the caller
 *  needs no hook of its own). */
val GraphSplitScope = FC<PropsWithChildren> { props ->
    val channel = useMemo { GraphSplitChannel() }
    GraphSplitContext.Provider {
        value = channel
        +props.children
    }
}

/** The current split published in this scope, or null when nothing is hovered. */
fun useGraphSplit(): GraphSplit? {
    val channel = use(GraphSplitContext)
    var split by useState(channel?.value)
    useEffectWithCleanup(channel) {
        if (channel == null) return@useEffectWithCleanup
        split = channel.value
        val unsubscribe = channel.subscribe { split = it }
        onCleanup { unsubscribe() }
    }
    return split
}

/** A side chip of a [CursorReadout]: [text] is its plain text (used to
 *  estimate its width), [content] renders it (defaults to the text). */
class ReadoutChip(
    val text: String,
    val color: Color,
    val content: ChildrenBuilder.() -> Unit = { +text },
)

/**
 * The value under a chart's cursor, for the chart's axis-label row: a value
 * chip centered on the cursor (clamped into the chart), flanked by chips for
 * the regions [below] and [above] it, joined to it so the three read as one
 * "from this point on" unit. A side chip that would run past the chart edge
 * is left out rather than pushing the value off the cursor; the other side,
 * being the complement, still tells the story.
 *
 * Widths are estimated (~7.5px per glyph at 12px, bold) — good enough to decide
 * visibility and which axis ticks to hide ([covered]); the placement itself
 * is exact CSS.
 */
class CursorReadout(
    val cursorPx: Double,
    val chartWidth: Double,
    val label: String,
    val below: ReadoutChip?,
    val above: ReadoutChip?,
    val fontScale: Double = 1.0,
) {
    private val glyph = 7.5 * fontScale
    private val pad = 5.0 * fontScale
    private fun width(text: String) = text.length * glyph + 2 * pad
    private val valueWidth = width(label)
    private val valueLeft = (cursorPx - valueWidth / 2).coerceIn(0.0, maxOf(0.0, chartWidth - valueWidth))
    private val showBelow = below != null && valueLeft - width(below.text) >= 0
    private val showAbove = above != null && valueLeft + valueWidth + width(above.text) <= chartWidth

    /** The horizontal span (chart px) the readout occupies. */
    val covered = (if (showBelow) valueLeft - width(below!!.text) else valueLeft)..
        (if (showAbove) valueLeft + valueWidth + width(above!!.text) else valueLeft + valueWidth)

    fun render(cb: ChildrenBuilder) = with(cb) {
        // Wrapper: positioning only, sized to the value chip. The value chip is a
        // separate child on a higher layer so the side chips can tuck under its
        // rounded corners (a child can't paint beneath its own parent's background).
        div {
            key = "readout"
            style = js.objects.unsafeJso {
                left = cursorPx.px
                top = 50.pct
                // Centered on the cursor, but kept fully inside the chart.
                transform = "translate(max(${-cursorPx}px, min(-50%, calc(${chartWidth - cursorPx}px - 100%))), -50%)"
                    .unsafeCast<Transform>()
            }
            css {
                position = Position.absolute
                whiteSpace = WhiteSpace.nowrap
                fontWeight = integer(600)
            }
            div {
                css {
                    position = Position.relative
                    zIndex = integer(1)
                    backgroundColor = Color("#555555")
                    color = Color("#FFFFFF")
                    borderRadius = RADIUS.px
                    padding = Padding(0.px, pad.px)
                }
                +label
            }
            fun side(chip: ReadoutChip, onLeft: Boolean) = div {
                css {
                    position = Position.absolute
                    top = 0.px
                    bottom = 0.px
                    // Extends RADIUS under the value chip, padded so the text stays put.
                    if (onLeft) {
                        right = 100.pct
                        marginRight = (-RADIUS).px
                        padding = Padding(0.px, (pad + RADIUS).px, 0.px, pad.px)
                        borderTopLeftRadius = RADIUS.px
                        borderBottomLeftRadius = RADIUS.px
                    } else {
                        left = 100.pct
                        marginLeft = (-RADIUS).px
                        padding = Padding(0.px, pad.px, 0.px, (pad + RADIUS).px)
                        borderTopRightRadius = RADIUS.px
                        borderBottomRightRadius = RADIUS.px
                    }
                    backgroundColor = chip.color
                    color = Color("#333333")
                }
                chip.content(this)
            }
            if (showBelow) side(below!!, onLeft = true)
            if (showAbove) side(above!!, onLeft = false)
        }
    }

    private companion object {
        const val RADIUS = 3.0
    }
}
