package components.presenter

import hooks.useWebSocket
import payloads.responses.WSData
import react.FC
import tools.confido.state.PresentationPV
import tools.confido.state.PresentationWSData
import tools.confido.state.PresenterView

/**
 * Presenter page for a live presentation. Acts as a proxy that subscribes
 * to the room's presentation WebSocket and delegates rendering to whatever
 * the current effective view is (slide, group prediction, question, etc.).
 *
 * Note: We avoid referencing [view2page] directly here to prevent a
 * recursive type-checking issue (since view2page's lazy init includes PresentationPP).
 * Instead, we access it at render-time via a helper that breaks the static dependency.
 */
val PresentationPP = FC<PresenterPageProps<PresentationPV>> { props ->
    val roomId = props.view.room.id
    val ws = useWebSocket<PresentationWSData>("/state/rooms/$roomId/presentation")

    if (ws is WSData) {
        val innerView: PresenterView? = ws.data.runState?.currentView
        if (innerView != null) {
            lookupPresenterPage(innerView) {
                this.view = innerView
            }
        }
    }
}

/** Look up and render a presenter page for the given view, avoiding static dependency on [view2page]. */
private fun react.ChildrenBuilder.lookupPresenterPage(
    view: PresenterView,
    block: PresenterPageProps<PresenterView>.() -> Unit,
) {
    val comp: PresenterPageType = getPresenterPageComponent(view)
    comp { block() }
}
