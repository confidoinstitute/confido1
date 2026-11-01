package tools.confido.web.utils.panzoom1d

import js.objects.unsafeJso as jso
import web.dom.document
import web.events.AddEventListenerOptions
import web.events.EventType
import web.events.addEventHandler
import web.mouse.WheelEvent
import web.navigator.navigator
import web.storage.localStorage

/**
 * When to show the "Ctrl+scroll to zoom" hint: only when a wheel gesture *starts*
 * over a pan/zoom surface (the user was pointing at it), never for a page scroll
 * that merely carries a graph under a resting cursor; at most once per
 * [COOLDOWN_MS]; and never again once the user has wheel-zoomed. [ZoomHint.onShow]
 * receives the viewport position to anchor at (the cursor stays put while the
 * page scrolls, so the hint does too), or null to hide.
 */
object ZoomHint {
    private const val GESTURE_GAP_MS = 400.0
    private const val COOLDOWN_MS = 30_000.0
    private const val DONE_KEY = "zoomHintDone"

    val modifierLabel = if (navigator.userAgent.contains("Mac")) "⌘" else "Ctrl"

    var onShow: (Pair<Double, Double>?) -> Unit = {}
    private var installed = false
    private var lastWheelTs = Double.NEGATIVE_INFINITY
    private var gestureStart = false
    private var lastShownTs = Double.NEGATIVE_INFINITY
    private var done = localStorage.getItem(DONE_KEY) != null

    /** Gesture detection needs every wheel event on the page, not just ours. The
     *  capture listener runs before any surface's own handler for the same event. */
    fun install() {
        if (installed) return
        installed = true
        document.addEventHandler(EventType<WheelEvent>("wheel"), jso<AddEventListenerOptions> {
            capture = true; passive = true
        }) { ev: WheelEvent ->
            val ts = ev.timeStamp.toDouble()
            gestureStart = ts - lastWheelTs > GESTURE_GAP_MS
            lastWheelTs = ts
        }
    }

    fun onPlainWheel(ev: WheelEvent) {
        val ts = ev.timeStamp.toDouble()
        if (done || !gestureStart || ts - lastShownTs < COOLDOWN_MS) return
        lastShownTs = ts
        onShow(ev.clientX.toDouble() to ev.clientY.toDouble())
    }

    fun onWheelZoom() {
        if (!done) {
            done = true
            localStorage.setItem(DONE_KEY, "1")
        }
        onShow(null)
    }
}
