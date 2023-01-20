package components.rooms

import components.AppStateContext
import hooks.useRunCoroutine
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Collapse
import payloads.requests.BaseRoomInformation
import react.*
import react.router.useNavigate
import kotlin.coroutines.EmptyCoroutineContext

val NewRoom = FC<Props> {
    var error by useState(false)
    val stale = useContext(AppStateContext).stale

    val navigate = useNavigate()

    val create = useRunCoroutine()

    fun createRoom(information: BaseRoomInformation) = create {
        Client.sendData("/rooms/add", information, onError = {error = true}) {
            val roomId: String = body()
            navigate("/room/$roomId")
        }
    }

    Collapse {
        this.`in` = error
        Alert {
            severity = AlertColor.error
            +"Room could not be created."
        }
    }

    RoomInfoForm {
        disabled = stale || create.running
        onSubmit = ::createRoom
    }
}
