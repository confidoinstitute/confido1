package components.redesign.basic

import emotion.react.css
import js.objects.unsafeJso as jso
import react.*
import react.dom.createPortal
import react.dom.html.ReactHTML.div
import web.cssom.*
import web.dom.document
import web.html.HTMLDivElement
import web.window.window

enum class TooltipPlacement {
    /** Place the tooltip above the anchor (its bottom touches `anchorY`). */
    TOP,
    /** Place the tooltip below the anchor (its top touches `anchorY`). */
    BOTTOM,
    /**
     * Prefer [TOP]; fall back to [BOTTOM] if the tooltip would clip the top of the viewport
     * but fits at the bottom. Requires post-mount measurement (one paint frame to settle).
     */
    AUTO,
}

external interface TooltipProps : PropsWithChildren {
    /** Whether the tooltip is currently shown. Defaults to `false`. */
    var open: Boolean?
    /** Anchor X in client (viewport) coordinates — same coordinate space as [web.uievents.MouseEvent.clientX]. */
    var anchorX: Double?
    /** Anchor Y in client (viewport) coordinates. */
    var anchorY: Double?
    /** Placement preference. Default [TooltipPlacement.AUTO]. */
    var placement: TooltipPlacement?
}

/**
 * Reusable, portal-based tooltip. Renders into `document.body` with `position: fixed`, so it
 * escapes any `overflow: hidden` / transformed ancestors of the calling component.
 *
 * This component exposes a programmatic API: callers control visibility (`open`) and the
 * anchor point in viewport coordinates (`anchorX`/`anchorY`). This makes it usable both for
 * regular DOM-anchored tooltips (caller computes the rect via `getBoundingClientRect()`) and
 * for "virtual" anchors such as a hovered element drawn on a `<canvas>`.
 *
 * Behavior:
 *  - Horizontal: centered on `anchorX`, then clamped to keep the tooltip within
 *    `[8px, viewportWidth - 8px]`.
 *  - Vertical: see [TooltipPlacement]. For [TooltipPlacement.AUTO] the tooltip is rendered
 *    once with `visibility: hidden` to measure its size, then repositioned on the next frame.
 *  - The tooltip has `pointerEvents: none` so the cursor passes through it (avoids hover
 *    flicker when the tooltip overlaps the anchor).
 *  - No scroll/resize tracking: position is computed when `open` / `anchorX` / `anchorY`
 *    change. If the user scrolls during a hover, the tooltip will be slightly stale until
 *    the next anchor update.
 *
 * Default styling matches the chart tooltip in `extensions/update_scatter_plot/js/SlopePlot.kt`
 * for visual consistency.
 */
val Tooltip = FC<TooltipProps> { props ->
    val open = props.open == true
    val ax = props.anchorX
    val ay = props.anchorY
    val placement = props.placement ?: TooltipPlacement.AUTO

    val ref = useRef<HTMLDivElement>()
    var measured by useState<Pair<Double, Double>?>(null)

    useLayoutEffect(open, ax, ay, props.children) {
        if (!open || ax == null || ay == null) {
            if (measured != null) measured = null
            return@useLayoutEffect
        }
        val el = ref.current ?: return@useLayoutEffect
        val w = el.offsetWidth.toDouble()
        val h = el.offsetHeight.toDouble()
        val cur = measured
        if (cur == null || cur.first != w || cur.second != h) {
            measured = w to h
        }
    }

    if (!open || ax == null || ay == null) return@FC

    val viewportW = window.innerWidth.toDouble()
    val viewportH = window.innerHeight.toDouble()
    val (w, h) = measured ?: (0.0 to 0.0)

    val effective = when (placement) {
        TooltipPlacement.AUTO ->
            if (measured != null && (ay - h - 8) < 0.0 && (ay + h + 8) <= viewportH)
                TooltipPlacement.BOTTOM
            else
                TooltipPlacement.TOP
        else -> placement
    }

    val topPx: Double = if (effective == TooltipPlacement.TOP) ay - 8.0 else ay + 8.0
    val translateYPct: Length = if (effective == TooltipPlacement.TOP) (-100).pct else 0.pct

    // Horizontal clamp: keep tooltip within [8, viewportW - 8].
    val halfW = w / 2.0
    val leftPx: Double = if (w == 0.0) ax else ax.coerceIn(8.0 + halfW, (viewportW - 8.0 - halfW).coerceAtLeast(8.0 + halfW))

    +createPortal(
        Fragment.create {
            div {
                this.ref = ref
                css {
                    position = Position.fixed
                    backgroundColor = Color("rgba(255, 255, 255, 0.97)")
                    border = Border(1.px, LineStyle.solid, Color("#e2e8f0"))
                    borderRadius = 8.px
                    padding = Padding(8.px, 12.px)
                    fontSize = 12.px
                    lineHeight = 16.px
                    color = rgb(0, 0, 0, 0.85)
                    fontFamily = sansSerif
                    boxShadow = BoxShadow(0.px, 2.px, 8.px, Color("rgba(0, 0, 0, 0.12)"))
                    pointerEvents = None.none
                    zIndex = integer(2900)
                    maxWidth = "min(360px, 90vw)" as MaxWidth
                    whiteSpace = WhiteSpace.normal
                    wordBreak = WordBreak.breakWord
                }
                style = jso {
                    this.top = topPx.px
                    this.left = leftPx.px
                    this.transform = translate((-50).pct, translateYPct)
                    // Hide on the first render of an AUTO placement until we've measured.
                    if (placement == TooltipPlacement.AUTO && measured == null) {
                        this.visibility = Visibility.hidden
                    }
                }
                +props.children
            }
        },
        document.body.asDynamic()
    )
}
