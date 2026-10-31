package tools.confido.web.hooks

import js.objects.unsafeJso as jso
import react.*
import web.dom.document
import web.events.AddEventListenerOptions
import web.events.Event
import web.events.EventTarget
import web.events.EventType
import web.events.addEventHandler
import web.events.removeEventListener
import web.keyboard.KeyboardEvent
import web.window.window

/**
 * Listens for [types] on a global [target] (see [useWindowEventListener],
 * [useDocumentEventListener]) while mounted and [enabled] — the global
 * counterpart of the ref-based [useEventListener].
 *
 * The listener is registered once per (target, types, enabled, options) and
 * always invokes the latest [callback], so callers need neither a stable
 * function identity (for removal) nor effect deps (against stale closures).
 */
fun <E : Event> useGlobalEventListener(
    target: EventTarget,
    vararg types: EventType<E>,
    enabled: Boolean = true,
    capture: Boolean? = null,
    passive: Boolean? = null,
    callback: (E) -> Unit,
) {
    val callbackHolder = useRef(callback)
    callbackHolder.current = callback
    useEffectWithCleanup(target, types.joinToString(","), enabled, capture, passive) {
        if (!enabled) return@useEffectWithCleanup
        val opts = jso<AddEventListenerOptions> {
            capture?.let { this.capture = it }
            passive?.let { this.passive = it }
        }
        val listener = { event: E -> callbackHolder.current?.invoke(event); Unit }
        types.forEach { target.addEventHandler(it, opts, listener) }
        onCleanup { types.forEach { target.removeEventListener(it, listener, opts) } }
    }
}

/** [useGlobalEventListener] on `window`. */
fun <E : Event> useWindowEventListener(
    vararg types: EventType<E>,
    enabled: Boolean = true,
    capture: Boolean? = null,
    passive: Boolean? = null,
    callback: (E) -> Unit,
) = useGlobalEventListener(window, *types, enabled = enabled, capture = capture, passive = passive, callback = callback)

/** [useGlobalEventListener] on `document`. */
fun <E : Event> useDocumentEventListener(
    vararg types: EventType<E>,
    enabled: Boolean = true,
    capture: Boolean? = null,
    passive: Boolean? = null,
    callback: (E) -> Unit,
) = useGlobalEventListener(document, *types, enabled = enabled, capture = capture, passive = passive, callback = callback)

enum class ModifierKey(internal val eventField: String) {
    SHIFT("shiftKey"), CONTROL("ctrlKey"), ALT("altKey"), META("metaKey");

    /** Whether this modifier is held per [event] — any keyboard or mouse/pointer
     *  event, all of which carry the modifier state. */
    fun heldIn(event: Event) = event.asDynamic()[eventField] == true
}

/**
 * Whether modifier [key] is currently held (while [enabled]; false otherwise).
 *
 * Keyboard events track presses; pointer events, which carry the modifier
 * state too, resync it (e.g. a key pressed while focus was elsewhere); and a
 * window blur resets it, as a key released while the window is unfocused never
 * delivers its keyup. State is set only on actual changes, so the frequent
 * pointer events don't re-render the caller.
 */
fun useModifierKey(key: ModifierKey, enabled: Boolean = true): Boolean {
    var held by useState(false)
    fun sync(event: Event) {
        val now = key.heldIn(event)
        if (now != held) held = now
    }
    useDocumentEventListener(EventType<KeyboardEvent>("keydown"), EventType("keyup"), enabled = enabled) { sync(it) }
    useDocumentEventListener(EventType<Event>("pointermove"), EventType("pointerdown"),
        enabled = enabled, capture = true, passive = true) { sync(it) }
    useWindowEventListener(EventType<Event>("blur"), enabled = enabled) { if (held) held = false }
    return held && enabled
}
