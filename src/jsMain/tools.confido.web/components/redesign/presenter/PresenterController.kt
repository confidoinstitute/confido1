package tools.confido.web.components.redesign.presenter

import tools.confido.web.Client
import tools.confido.web.ApiError
import tools.confido.api.Api
import web.window.window
import tools.confido.web.components.usePresenterWindowActive
import tools.confido.web.components.presenter.getPresenterControlsComponent
import tools.confido.web.components.redesign.basic.Dialog
import tools.confido.web.components.redesign.basic.DialogMainButton
import tools.confido.web.components.redesign.basic.Message
import tools.confido.web.components.redesign.basic.MessageType
import tools.confido.web.components.redesign.basic.showMessage
import tools.confido.web.components.redesign.forms.Button
import tools.confido.web.components.redesign.forms.FormSection
import web.cssom.Margin
import web.cssom.integer
import web.cssom.px
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.p
import tools.confido.state.EmptyPV
import tools.confido.state.PresenterView
import tools.confido.utils.capFirst
import tools.confido.web.utils.runCoroutine
import web.window.WindowTarget
import web.window._blank

// Presenter-view updates are best-effort side channel writes — failures are
// deliberately swallowed rather than toasted.
suspend fun setPresenterView(view: PresenterView) {
    try { Client.call(Api.presenterSet(), view) } catch (e: ApiError) {}
}

/** Clear the session's presenter view entirely, marking the session as "not presenting". */
suspend fun stopPresenting() {
    try { Client.call(Api.presenterStop()) } catch (e: ApiError) {}
}

enum class  PresenterButtonState {
    NORMAL, SNACKBAR_OPEN, DIALOG_OPEN
}

fun openPresenterWindow() {
    // this seems to force new window (not tab) in both Firefox and Chrome
    window.open("/presenter", WindowTarget._blank, "popup")
}

external interface PresenterDialogProps : Props {
    var view: PresenterView
    var open: Boolean
    /** Whether a presenter window is already open (changes wording and confirm action). */
    var windowActive: Boolean
    var onClose: (()->Unit)?
    var onConfirm: ((PresenterView)->Unit)?
}

val PresenterDialog = FC<PresenterDialogProps> {props->
    // Local draft of the view, editable through the view's controls (if any)
    // before it is actually shown.
    var draft by useState(props.view)
    useEffect(props.view) { draft = props.view }
    // Derive the controls component from `draft`, the same value passed to it below —
    // never from props.view. The dialog is mounted permanently with EmptyPV and the
    // draft-syncing effect only runs after the commit, so on the first render after a
    // view is offered, draft still holds the previous (Empty) view; selecting the
    // component by props.view then renders e.g. GroupPredPC with an EmptyPV, which
    // crashes on the missing fields. With both derived from draft they cannot diverge
    // (the controls simply appear one commit later).
    val controlsComp = getPresenterControlsComponent(draft)
    Dialog {
        title = "Presentation mode"
        action = if (props.windowActive) "Show" else "Open"
        this.open = props.open
        onAction = {
            props.onConfirm?.invoke(draft)
        }
        onClose = { props.onClose?.invoke() }
        if (props.windowActive) {
            p {
                +"This will show the "
                PresenterViewDescription { view = props.view }
                +" in the open presentation window."
            }
        } else {
            p {
                +"This will open a new browser window showing the "
                PresenterViewDescription { view = props.view }
                +"."
            }
            p {
                + "This is most useful for presenting "
                +" things on a projector. You can simply drag the window to the projector screen and then make it"
                +" full screen by pressing F11."
                // + " Alternatively, you can copy a link to the presenter view and then e.g. open it on another"
                // + +" device."
            }
        }
        controlsComp?.let { comp ->
            FormSection {
                comp {
                    this.view = draft
                    this.disabled = false
                    this.onChange = { draft = it }
                }
            }
        }
        DialogMainButton {
            onClick =  { props.onConfirm?.invoke(draft) }
            +(if (props.windowActive) "Show in presentation window" else "Open presentation window")
        }
    }
}

class PresenterController(var onOffer: ((PresenterView, Boolean)->Unit)?=null) {

    /**
     * Offer a view for presentation. Normally, if a presenter window is already open,
     * the view is shown immediately; otherwise a confirmation dialog is shown first.
     * With [forceDialog], the dialog (including the view's configuration controls, if any)
     * is always shown - useful for views that need configuring before display.
     */
    fun offer(view: PresenterView, forceDialog: Boolean = false) {
        onOffer?.invoke(view, forceDialog)
    }
    fun set(view: PresenterView) {

        runCoroutine {
            setPresenterView(view)
        }
    }
    /** Stop presenting (clears the presenter view; the presenter window, if open, goes blank). */
    fun stop() {
        runCoroutine {
            stopPresenting()
        }
    }
}

val PresenterContext = react.createContext<PresenterController>()


val PresenterControllerProvider = FC<PropsWithChildren> { props->
    var offeredView by useState<PresenterView>()

    val ctl = useMemo { PresenterController()}
    // Captured at render: offer() runs from other components' event handlers, where
    // the use()-based accessors are off-limits.
    val presenterWindowActive = usePresenterWindowActive()
    fun offer(view: PresenterView, forceDialog: Boolean) {
        val desc = view.describe()
        if (presenterWindowActive && !forceDialog) {
            showMessage(Message("${desc.capFirst()} displayed in existing presentation window.", MessageType.SUCCESS))
            ctl.set(view)
            offeredView = null
        } else {
            offeredView = view
        }
    }
    ctl.onOffer = ::offer
    PresenterContext.Provider {
        value = ctl
        +props.children
        // NOTE: The dialog must be rendered unconditionally (with `open` toggling visibility).
        // Conditionally adding/removing the element used to remount the whole sibling route
        // tree (resetting e.g. question page tab state) whenever a presenter view was offered.
        PresenterDialog {
            key = "presenterDialog"
            this.view = offeredView ?: EmptyPV
            this.open = offeredView != null
            this.windowActive = presenterWindowActive
            onConfirm = { finalView ->
                ctl.set(finalView)
                offeredView = null
                if (!presenterWindowActive)
                    openPresenterWindow()
            }
            onClose = {
                offeredView = null
            }
        }
    }
}