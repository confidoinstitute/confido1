package components.redesign.rooms.dialog

import components.AppStateContext
import components.redesign.basic.Dialog
import components.redesign.basic.DialogCoreProps
import components.redesign.basic.DialogProps
import components.redesign.rooms.RoomSettings
import components.rooms.RoomContext
import components.showError
import hooks.EditEntityDialogProps
import payloads.requests.BaseRoomInformation
import react.FC
import react.useContext
import react.useState
import utils.runCoroutine

external interface EditRoomSettingsDialogProps: DialogCoreProps {
    var openSchedule: Boolean?
}

val EditRoomSettingsDialog = FC<EditRoomSettingsDialogProps> { props ->
    val room = useContext(RoomContext)
    val stale = useContext(AppStateContext).stale

    var baseInfo by useState<BaseRoomInformation?>(null)

    fun editRoom() = runCoroutine {
        Client.sendData("${room.urlPrefix}/edit", baseInfo, onError = { showError(it) }) { props.onClose?.invoke() }
    }

    Dialog {
        open = props.open
        onClose = props.onClose
        title = "Edit this room"
        action = "Save"
        disabledAction = (stale || baseInfo == null)
        onAction = ::editRoom

        RoomSettings {
            this.room = room
            onChange = {baseInfo = it}
            onSubmit = ::editRoom
            openSchedule = props.openSchedule
        }
    }
}
