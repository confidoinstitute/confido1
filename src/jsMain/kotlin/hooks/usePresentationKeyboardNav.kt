package hooks

import Client
import components.redesign.presenter.setPresenterView
import payloads.requests.NavigateDirection
import payloads.requests.NavigatePresentation
import react.useEffectOnceWithCleanup
import react.useRef
import rooms.Room
import tools.confido.refs.Ref
import tools.confido.state.PresentationPV
import web.dom.document
import web.events.Event
import web.events.EventType
import web.events.addEventHandler
import web.events.removeEventListener
import kotlin.js.Date

/**
 * Debounce interval for keyboard/presenter-remote navigation (in ms).
 * Prevents wireless presenter button bounce from triggering double-steps.
 */
private const val NAV_DEBOUNCE_MS = 250.0

/**
 * Installs a keyboard event handler on the document that navigates a running
 * presentation in the given room. Handles arrow keys, Page Up/Down, and Space
 * with debouncing to prevent button-bounce from wireless presenters.
 *
 * On each successful navigation, the session's presenter view is reset to
 * [PresentationPV] for the room, ensuring the presenter window stays on the
 * live presentation even if it was manually changed elsewhere.
 *
 * Safe to call from multiple components — each instance maintains its own
 * debounce state and coroutine lock.
 */
fun usePresentationKeyboardNav(roomId: String) {
    val submitLock = useCoroutineLock()
    val lastNavTime = useRef(0.0)
    val roomUrlPrefix = Room.urlPrefix(roomId)
    val roomRef = Ref<rooms.Room>(roomId)

    useEffectOnceWithCleanup {
        val handler = { e: Event ->
            val ke = e.asDynamic()
            val key = ke.key as? String ?: ""
            // Don't capture if user is typing in an input. isContentEditable covers the
            // markdown editor, where swallowing Space would break ordinary typing.
            val tagName = (ke.target?.tagName as? String)?.uppercase() ?: ""
            val editable = ke.target?.isContentEditable == true
            if (tagName != "INPUT" && tagName != "TEXTAREA" && !editable) {
                val now = Date.now()
                val elapsed = now - (lastNavTime.current ?: 0.0)
                val dir = when (key) {
                    "ArrowRight", "ArrowDown", " ", "PageDown" -> NavigateDirection.NEXT
                    "ArrowLeft", "ArrowUp", "PageUp" -> NavigateDirection.PREV
                    else -> null
                }
                if (dir != null) {
                    e.preventDefault()
                    if (elapsed >= NAV_DEBOUNCE_MS) {
                        lastNavTime.current = now
                        submitLock {
                            Client.sendData("$roomUrlPrefix/presentation/navigate",
                                NavigatePresentation(dir),
                                onError = {}) {
                                setPresenterView(PresentationPV(roomRef))
                            }
                        }
                    }
                }
            }
        }
        document.addEventHandler(EventType("keydown"), handler)
        onCleanup { document.removeEventListener(EventType<Event>("keydown"), handler) }
    }
}
