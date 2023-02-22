package components.rooms

import components.AppStateContext
import components.showError
import hooks.useCoroutineLock
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Collapse
import payloads.requests.BaseRoomInformation
import react.*
import react.router.useNavigate
import utils.roomUrl
import kotlin.coroutines.EmptyCoroutineContext

val NewRoom = FC<Props> {
    val stale = useContext(AppStateContext).stale

    val navigate = useNavigate()

    val create = useCoroutineLock()

    fun createRoom(information: BaseRoomInformation) = create {
        Client.sendData("/rooms/add", information, onError = {showError?.invoke(it)}) {
            val roomId: String = body()
            navigate(roomUrl(roomId))
        }
    }

    RoomInfoForm {
        disabled = stale || create.running
        onSubmit = ::createRoom
    }
}
