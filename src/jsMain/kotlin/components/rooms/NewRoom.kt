package components.rooms

import components.AppStateContext
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Collapse
import payloads.requests.BaseRoomInformation
import react.*
import react.router.useNavigate
import utils.postJson
import kotlin.coroutines.EmptyCoroutineContext

val NewRoom = FC<Props> {
    var creating by useState(false)
    var error by useState(false)
    val stale = useContext(AppStateContext).stale

    val navigate = useNavigate()

    fun createRoom(information: BaseRoomInformation) {
        CoroutineScope(EmptyCoroutineContext).launch {
            creating = true
            try {
                val roomId: String = Client.postDataAndReceive("/rooms/add", information) {
                    expectSuccess = true
                }
                navigate("/room/$roomId")
            } catch (e: Throwable) {
                error = true
            } finally {
                creating = false
            }
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
        disabled = creating
        onSubmit = ::createRoom
    }
}
