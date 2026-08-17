package tools.confido.web.components.redesign

import tools.confido.web.Client
import tools.confido.web.components.redesign.basic.PageHeader
import tools.confido.web.components.redesign.basic.Stack
import tools.confido.web.components.redesign.basic.showError
import tools.confido.web.components.redesign.forms.Button
import tools.confido.web.components.redesign.rooms.RoomSettings
import web.cssom.Display
import web.cssom.Margin
import web.cssom.integer
import web.cssom.px
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import io.ktor.client.call.*
import tools.confido.payloads.requests.BaseRoomInformation
import react.FC
import react.Props
import react.router.useNavigate
import react.use
import react.useState
import tools.confido.api.Api
import tools.confido.web.utils.roomUrl

val CreateRoom = FC<Props> {

    val navigate = useNavigate()
    val create = useCoroutineLock()

    var baseInfo by useState<BaseRoomInformation?>(null)

    fun createRoom() = create {
        baseInfo?.let {
            val roomId = Client.call(Api.roomAdd(), it)
            navigate(roomUrl(roomId))
        }
    }

    PageHeader {
        
        title = "Create a new room"
        navigateBack = "/"
        action = "Create"
        disabledAction = baseInfo == null
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
