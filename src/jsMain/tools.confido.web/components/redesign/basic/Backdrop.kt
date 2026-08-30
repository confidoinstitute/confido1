package tools.confido.web.components.redesign.basic

import tools.confido.web.components.redesign.transitions.*
import web.cssom.*
import web.html.*
import emotion.react.*
import react.*
import react.dom.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import tools.confido.web.utils.*
import web.dom.ElementId
import web.dom.document
import web.events.Event

external interface BackdropProps: HTMLAttributes<HTMLDivElement> {
    var appear: Boolean?
    var `in`: Boolean
    /** Defaults to the historic 2000. */
    var zIndex: Int?
    /** Dim opacity; defaults to [BASE_BACKDROP_DIM]. */
    var dim: Double?
}

typealias BackdropClickHandler = ()->Unit
class BackdropCounter(val add: (ElementId, BackdropClickHandler?) -> Unit, val del: (ElementId) -> Unit)

val BackdropContext = createContext<BackdropCounter>()
/** Ids of the currently open modal layers, in opening order (bottom-most first). */
val BackdropLayersContext = createContext<List<ElementId>>()

/** Page-level dim under the bottom-most modal layer. */
const val BASE_BACKDROP_DIM = 0.5
/**
 * Lighter dim between stacked dialogs: enough to draw a boundary between two
 * white panels without compounding towards black (dims compose as 1 - ∏(1-αᵢ),
 * so a confirm-over-edit stack darkens the page to 0.625 rather than 0.75).
 */
const val INTER_DIALOG_DIM = 0.25

const val MODAL_LAYER_STEP = 20
/** z-index for the modal panel at stacking [layer] (0 = bottom-most). */
fun modalZIndex(layer: Int) = 2100 + MODAL_LAYER_STEP * layer
/**
 * z-index for the backdrop directly below [layer]'s panel. Layer 0 keeps the
 * historic 2000 so that elements designed to sit above the page dim but below
 * dialogs (e.g. the tablet overlay sidebar at 2050) are not covered by it.
 */
fun backdropZIndex(layer: Int) = if (layer == 0) 2000 else modalZIndex(layer) - 10

val BackdropProvider = FC<PropsWithChildren> {
    val (counter, setCounter) = useState(mapOf<ElementId, BackdropClickHandler?>())

    val counterSetter = useMemo {
        BackdropCounter(
            add = { id, onClick -> setCounter { it.plus(id to onClick) } },
            del = { id -> setCounter { it.minus(id) } },
        )
    }

    // One backdrop per open modal layer, each just below its layer's panel, so
    // stacked dialogs are separated by a dim veil. Slots are ordinal-indexed and
    // only ever grow: a closed layer's slot stays mounted with `in`=false so its
    // Fade can play out (an exited Fade renders nothing).
    val slots = useRef(0)
    slots.current = maxOf(slots.current ?: 0, counter.size)
    val handlers = counter.values.toList()
    repeat(slots.current ?: 0) { layer ->
        Backdrop {
            key = "backdrop$layer"
            appear = false
            this.`in` = layer < counter.size
            zIndex = backdropZIndex(layer)
            if (layer > 0) dim = INTER_DIALOG_DIM
            onClick = {
                // Click directly on backdrop, not descendants. Each backdrop closes
                // only the layer directly above it; as the topmost backdrop covers
                // all lower ones, a click on the dim area dismisses just the
                // topmost dialog, leaving the rest of the stack open.
                if (it.target == it.currentTarget)
                    handlers.getOrNull(layer)?.invoke()
            }
        }
    }
    val layerIds = useMemo(counter) { counter.keys.toList() }
    BackdropContext.Provider {
        // The keys (also on the Backdrops above) matter: without them, the first
        // slot appearing would shift this provider's position and index-based
        // reconciliation would remount the entire app subtree under it.
        key = "content"
        value = counterSetter
        BackdropLayersContext.Provider {
            value = layerIds
            +it.children
        }
    }
}

val Backdrop = FC<BackdropProps> {props ->
    val nodeRef = useRef<HTMLDivElement>()
    +createPortal(
        Fade.create {
            this.`in` = props.`in`
            timeout = 250
            appear = props.appear ?: true
            mountOnEnter = true
            unmountOnExit = true
            this.nodeRef = nodeRef
            Fragment {
                div {
                    ref = nodeRef
                    +props.except("in", "appear", "zIndex", "dim")
                    css(override = props) {
                        position = Position.fixed
                        top = 0.px
                        left = 0.px
                        right = 0.px
                        bottom = 0.px
                        overflow = Overflow.hidden
                        backgroundColor = rgb(0, 0, 0, props.dim ?: BASE_BACKDROP_DIM)
                        zIndex = integer(props.zIndex ?: 2000)
                    }
                }
                Global {
                    styles {
                        "body" {
                            overflow = Overflow.hidden
                        }
                    }
                }
            }
        }, document.body.asDynamic())
}

/**
 * Register an [open] modal layer with the shared backdrop stack. Returns the
 * z-index the caller's panel should render at (directly above its own backdrop).
 * While the layer is not registered — including the close animation, which
 * outlives [open] — this is the z-index of a would-be new topmost layer, so
 * exiting dialogs stay above the remaining stack.
 */
fun useBackdrop(open: Boolean, onClick: BackdropClickHandler? = null): Int {
    val setBackground = use(BackdropContext)!!
    val layers = use(BackdropLayersContext) ?: emptyList()
    val dialogId = useId()
    // The handler goes through a ref so the effect need not depend on its identity:
    // re-registering on every render would move the layer to the end of the
    // insertion-ordered registry and scramble the stacking order.
    val onClickRef = useRef(onClick)
    onClickRef.current = onClick
    useEffectWithCleanup(open) {
        if (open) setBackground.add(dialogId) { onClickRef.current?.invoke() }
        else setBackground.del(dialogId)
        onCleanup {
            setBackground.del(dialogId)
        }
    }
    val layer = layers.indexOf(dialogId)
    return modalZIndex(if (layer >= 0) layer else layers.size)
}

/**
 * z-index just above every currently open modal layer, for popovers that stack
 * on top of whatever is open without registering a dim layer of their own.
 */
fun useAboveModalsZIndex(): Int {
    val layers = use(BackdropLayersContext) ?: emptyList()
    return modalZIndex(layers.size)
}
