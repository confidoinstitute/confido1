package components.redesign.rooms.dialog

import components.AppStateContext
import components.redesign.basic.Dialog
import components.redesign.basic.DialogCoreProps
import components.redesign.basic.DialogProps
import components.redesign.basic.showError
import components.redesign.rooms.RoomContext
import components.redesign.rooms.RoomSettings
import hooks.EditEntityDialogProps
import payloads.requests.BaseRoomInformation
import react.FC
import react.use
import react.useState
import utils.runCoroutine

external interface EditRoomSettingsDialogProps: DialogCoreProps {
    var openSchedule: Boolean?
}

val EditRoomSettingsDialog = FC<EditRoomSettingsDialogProps> { props ->
    val room = use(RoomContext)!!
    val stale = use(AppStateContext)!!.stale

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
