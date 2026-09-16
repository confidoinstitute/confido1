package tools.confido.web.components

import tools.confido.web.Client
import tools.confido.web.components.basic.PageHeader
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.basic.showError
import tools.confido.web.components.forms.Button
import tools.confido.web.components.rooms.RoomSettings
import web.cssom.Display
import web.cssom.Margin
import web.cssom.integer
import web.cssom.px
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import io.ktor.client.call.*
import tools.confido.payloads.requests.BaseRoomInformation
import js.array.component1
import js.array.component2
import react.FC
import react.Props
import react.router.useNavigate
import react.useEffectWithCleanup
import web.window.window
import react.router.dom.useSearchParams
import react.use
import react.useState
import tools.confido.api.Api
import tools.confido.refs.Ref
import tools.confido.rooms.Room
import tools.confido.web.utils.roomUrl

external interface CreateRoomProps : Props {
    /** Create a group container instead of an ordinary room. */
    var isGroup: Boolean?
}

val CreateRoom = FC<CreateRoomProps> { props ->
    val isGroup = props.isGroup ?: false
    val navigate = useNavigate()
    val create = useCoroutineLock()
    // "Create room in this group" entry points preselect the parent via ?group=
    val (searchParams, _) = useSearchParams()
    val groupId = searchParams.get("group")

    var baseInfo by useState<BaseRoomInformation?>(null)

    // Same per-page convention as Room/Question: entering (or leaving) the page
    // resets scroll, so opening the form from a scrolled list starts at the top.
    // Keyed on isGroup: /new_room <-> /new_group reuse this component instance.
    useEffectWithCleanup(isGroup) {
        window.scrollTo(0.0, 0.0)
        onCleanup {
            window.scrollTo(0.0, 0.0)
        }
    }

    fun createRoom() = create {
        baseInfo?.let {
            val roomId = Client.call(Api.roomAdd(), it)
            navigate(roomUrl(roomId))
        }
    }

    PageHeader {
        title = if (isGroup) "Create a new group" else "Create a new room"
        navigateBack = "/"
        action = "Create"
        disabledAction = baseInfo == null
        onAction = ::createRoom
    }

    ThemeProvider {
        theme = { theme -> theme.copy(colors = theme.colors.copy(form = AltFormColors)) }
        RoomSettings {
            this.isGroup = isGroup
            this.initialParentRoom = if (!isGroup) groupId?.let { Ref<Room>(it) } else null
            onChange = { baseInfo = it }
            onSubmit = ::createRoom
        }
    }
}
