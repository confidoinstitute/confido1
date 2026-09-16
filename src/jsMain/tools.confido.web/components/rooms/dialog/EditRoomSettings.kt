package tools.confido.web.components.rooms.dialog

import tools.confido.web.Client
import tools.confido.web.components.basic.Dialog
import tools.confido.web.components.basic.DialogCoreProps
import tools.confido.web.components.basic.DialogProps
import tools.confido.web.components.basic.showError
import tools.confido.web.components.rooms.RoomContext
import tools.confido.web.components.rooms.RoomSettings
import tools.confido.web.hooks.EditEntityDialogProps
import tools.confido.payloads.requests.BaseRoomInformation
import react.FC
import react.use
import react.useState
import tools.confido.api.Api
import tools.confido.refs.ref
import tools.confido.web.utils.runCoroutine

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
