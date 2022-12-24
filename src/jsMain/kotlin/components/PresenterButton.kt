package components

import components.rooms.RoomContext
import icons.ProjectorScreenIcon
import icons.ProjectorScreenOutlineIcon
import mui.material.*
import react.*
import rooms.Room
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.PresenterView
import tools.confido.state.clientState

external interface PresenterButtonProps : Props {
    var view: PresenterView
    var what: String // description (noun phrase) of the thing being shown (e.g. "Crowd prediction graph")
    var icon: ReactNode?
    var iconActive: ReactNode?
}

fun setPresenterView(room: Ref<Room>, view: PresenterView) {

}

enum class  PresenterButtonState {
    NORMAL, SNACKBAR_OPEN, DIALOG_OPEN
}

val PresenterButton = FC <PresenterButtonProps> { props->
    var state by useState(PresenterButtonState.NORMAL)
    val room = useContext(RoomContext)
    Tooltip {
        title = ReactNode("Show ${props.what} in a presenter window")
        IconButton {
            if (room.presenterInfo.view == props.view)
                +(props.iconActive ?: props.icon ?: ProjectorScreenIcon.create())
            else
                +(props.icon ?: ProjectorScreenOutlineIcon.create())
            onClick = {
                if (room.presenterInfo.isPresenterWindowOpen) {
                    setPresenterView(room.ref, props.view)
                    state = PresenterButtonState.SNACKBAR_OPEN
                } else {
                    state = PresenterButtonState.DIALOG_OPEN
                }
            }
        }
    }
    Snackbar {
        message = ReactNode("${props.what} displayed in existing presenter window.")
        action = Button.create {
            +"Open new presenter window"
        }
        autoHideDuration = 5000
        open = (state == PresenterButtonState.SNACKBAR_OPEN)
        onClose = { _,_ -> if (state == PresenterButtonState.SNACKBAR_OPEN) state = PresenterButtonState.NORMAL }
    }
    Dialog {
        open = (state == PresenterButtonState.DIALOG_OPEN)
        DialogTitleWithCloseButton {
            +"Open in presenter view"
            onClose = { if (state == PresenterButtonState.DIALOG_OPEN) state = PresenterButtonState.NORMAL }
        }
        DialogContentText{
            // TODO  link to an explanatory article
            +"This will open a new browser window/tab showing the ${props.what}. This is most useful for showing "
            +" things on a projector. You can simply drag the window/tab to the projector screen and then make it"
            +" full screen. Alternatively, you can copy a link to the presenter view and then e.g. open it on another"
            +" device."
        }
        DialogActions {
            Button {
                +"Open presenter window"
                onClick = {}
            }
            Button {
                +"Copy link"
                onClick = {}
            }
        }
    }
}