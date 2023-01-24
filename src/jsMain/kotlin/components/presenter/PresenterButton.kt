package components.presenter

import browser.window
import components.AppStateContext
import components.DialogCloseButton
import components.rooms.RoomContext
import icons.ProjectorScreenIcon
import icons.ProjectorScreenOutlineIcon
import mui.material.*
import react.*
import tools.confido.state.EmptyPV
import tools.confido.state.PresenterView
import tools.confido.utils.capFirst
import tools.confido.utils.uncapFirst

external interface PresenterButtonProps : Props {
    var view: PresenterView
}

fun setPresenterView(view: PresenterView) {
    Client.postData("/presenter/set", view)
}

enum class  PresenterButtonState {
    NORMAL, SNACKBAR_OPEN, DIALOG_OPEN
}

fun openPresenterWindow() {
    // this seems to force new window (not tab) in both Firefox and Chrome
    window.open("/presenter", "_blank", "popup")
}

fun ChildrenBuilder.usePresenterOpener(): ((PresenterView)->Unit) {
    val (appState, stale) = useContext(AppStateContext)
    var state by useState(PresenterButtonState.NORMAL)
    var view by useState<PresenterView>(EmptyPV)
    val desc = view.describe()
    Snackbar {
        message = ReactNode("${desc.capFirst()} displayed in existing presentation window.")
        action = Button.create {
            +"Open new presentation window"
            color = ButtonColor.secondary
            onClick = { openPresenterWindow() }
        }
        autoHideDuration = 5000
        open = (state == PresenterButtonState.SNACKBAR_OPEN)
        onClose = { _,_ -> if (state == PresenterButtonState.SNACKBAR_OPEN) state = PresenterButtonState.NORMAL }
    }
    Dialog {
        open = (state == PresenterButtonState.DIALOG_OPEN)
        DialogTitle {
            +"Presentation mode"
            DialogCloseButton {
                onClose = { if (state == PresenterButtonState.DIALOG_OPEN) state = PresenterButtonState.NORMAL }
            }
        }
        DialogContent {
            DialogContentText {
                // TODO  link to an explanatory article
                +"This will open a new browser window showing the ${desc}."
            }
            DialogContentText {

                + "This is most useful for presenting "
                +" things on a projector. You can simply drag the window to the projector screen and then make it"
                +" full screen by pressing F11."

                // TODO not implemented: sharable presentation view
                // + " Alternatively, you can copy a link to the presenter view and then e.g. open it on another"
                // + +" device."
            }
        }
        DialogActions {
            mui.material.Button {
                +"Open presentation window"
                onClick = { setPresenterView(view); openPresenterWindow(); state = PresenterButtonState.NORMAL }
            }
            //Button {
            //    +"Copy link"
            //    onClick = {}
            //}
        }
    }
    return {
        view = it
        state = if (appState.presenterWindowActive) {
            setPresenterView(it)
            PresenterButtonState.SNACKBAR_OPEN
        } else {
            PresenterButtonState.DIALOG_OPEN
        }
    }
}

val PresenterButton = FC <PresenterButtonProps> { props->
    val room = useContext(RoomContext)
    val (appState, stale) = useContext(AppStateContext)
    val isActive = appState.session.presenterInfo?.view == props.view && appState.presenterWindowActive
    val openPresenter = usePresenterOpener()
    Tooltip {
        title = if (isActive)
            ReactNode("${props.view.describe().capFirst()} is shown in a presentation window. Click to hide.")
        else
            ReactNode("Show ${props.view.describe().uncapFirst()} in a presentation window")
        IconButton {
            disabled = stale
            if (isActive)
                ProjectorScreenIcon{}
            else
                ProjectorScreenOutlineIcon{}
            onClick = {
                if (isActive)
                    setPresenterView(EmptyPV)
                else
                    openPresenter(props.view)
            }
        }
    }
}