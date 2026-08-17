package components.redesign.rooms.dialog

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
import tools.confido.api.Api
import tools.confido.refs.ref
import utils.runCoroutine

external interface EditRoomSettingsDialogProps: DialogCoreProps {
    var openSchedule: Boolean?
}

val EditRoomSettingsDialog = FC<EditRoomSettingsDialogProps> { props ->
    val room = use(RoomContext)!!

    var baseInfo by useState<BaseRoomInformation?>(null)

    fun editRoom() = runCoroutine {
        baseInfo?.let {
            Client.call(Api.roomEdit(room.ref), it)
            props.onClose?.invoke()
        }
    }

    Dialog {
        open = props.open
        onClose = props.onClose
        title = "Edit this room"
        action = "Save"
        disabledAction = baseInfo == null
        onAction = ::editRoom

        RoomSettings {
            this.room = room
            onChange = {baseInfo = it}
            onSubmit = ::editRoom
            openSchedule = props.openSchedule
        }
    }
}
