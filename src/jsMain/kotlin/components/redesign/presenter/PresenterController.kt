package components.redesign.presenter

import browser.window
import components.AppStateContext
import components.Message
import components.MessageType
import components.redesign.basic.Dialog
import components.redesign.basic.DialogMainButton
import components.redesign.forms.Button
import components.showMessage
import csstype.Margin
import csstype.integer
import csstype.px
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.p
import tools.confido.state.PresenterView
import tools.confido.utils.capFirst
import utils.runCoroutine

suspend fun setPresenterView(view: PresenterView) {
    Client.sendData("/presenter/set", view, onError ={}) {}
}

enum class  PresenterButtonState {
    NORMAL, SNACKBAR_OPEN, DIALOG_OPEN
}

fun openPresenterWindow() {
    // this seems to force new window (not tab) in both Firefox and Chrome
    window.open("/presenter", "_blank", "popup")
}

external interface PresenterDialogProps : Props {
    var view: PresenterView
    var open: Boolean
    var onClose: (()->Unit)?
    var onConfirm: (()->Unit)?
}

val PresenterDialog = FC<PresenterDialogProps> {props->
    Dialog {
        title = "Presentation mode"
        action = "Open"
        this.open = props.open
        onAction = {
            props.onConfirm?.invoke()
        }
        onClose = { props.onClose?.invoke() }
        p {
            +"This will open a new browser window showing the ${props.view.describe()}."
        }
        p {
            + "This is most useful for presenting "
            +" things on a projector. You can simply drag the window to the projector screen and then make it"
            +" full screen by pressing F11."
            // + " Alternatively, you can copy a link to the presenter view and then e.g. open it on another"
            // + +" device."
        }
        DialogMainButton {
            onClick =  { props.onConfirm?.invoke() }
            +"Open presentation window"
        }
    }
}

class PresenterController(var onOffer: ((PresenterView)->Unit)?=null) {

    fun offer(view:PresenterView ) {
        onOffer?.invoke(view)
    }
    fun set(view: PresenterView) {

        runCoroutine {
            setPresenterView(view)
        }
    }
}

val PresenterContext = react.createContext<PresenterController>()


val PresenterControllerProvider = FC<PropsWithChildren> { props->
    var offeredView by useState<PresenterView>()
    val (appState, stale) = useContext(AppStateContext)

    val ctl = useMemo { PresenterController()}
    fun offer(view: PresenterView) {
        val desc = view.describe()
        if (appState.presenterWindowActive) {
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
        offeredView?.let { view->
            PresenterDialog {
                key="presenterDialog"
                this.view = view
                this.open = true
                onConfirm = {
                    ctl.set(view)
                    offeredView = null
                    if (!appState.presenterWindowActive)
                        openPresenterWindow()
                }
                onClose = {
                    offeredView = null
                }
            }
        }
    }
}