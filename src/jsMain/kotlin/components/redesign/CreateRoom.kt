package components.redesign

import components.AppStateContext
import components.redesign.basic.PageHeader
import components.redesign.basic.Stack
import components.redesign.forms.Button
import components.redesign.rooms.RoomSettings
import components.showError
import csstype.Display
import csstype.Margin
import csstype.integer
import csstype.px
import emotion.react.css
import hooks.useCoroutineLock
import io.ktor.client.call.*
import payloads.requests.BaseRoomInformation
import react.FC
import react.Props
import react.dom.html.ButtonType
import react.router.useNavigate
import react.useContext
import react.useState
import utils.roomUrl

val CreateRoom = FC<Props> {
    val stale = useContext(AppStateContext).stale

    val navigate = useNavigate()
    val create = useCoroutineLock()

    var baseInfo by useState<BaseRoomInformation?>(null)

    fun createRoom() = create {
        baseInfo?.let {
            Client.sendData("/rooms/add", it, onError = { showError?.invoke(it) }) {
                val roomId: String = body()
                navigate(roomUrl(roomId))
            }
        }
    }

    PageHeader {
        title = "Create a new room"
        navigateBack = "/"
        action = "Create"
        disabledAction = stale || baseInfo == null
        onAction = ::createRoom
    }

    ThemeProvider {
        theme = { theme -> theme.copy(colors = theme.colors.copy(form = AltFormColors)) }
        RoomSettings {
            onChange = { baseInfo = it }
            onSubmit = ::createRoom
        }
    }
}
