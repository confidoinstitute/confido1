package tools.confido.state

import browser.window
import kotlinx.serialization.ExperimentalSerializationApi
import ClientExtension
import rooms.Room
import rooms.RoomPermission
import tools.confido.serialization.confidoJSON
import kotlinx.serialization.json.decodeFromDynamic

class ClientState(val sentState: SentState)
    : GlobalState(), BaseState by sentState {
    val session by sentState::session
    val myPredictions by sentState::myPredictions
    override val extensions: List<ClientExtension> get() =
        appConfig.enabledExtensionIds.mapNotNull { ClientExtension.registry[it] }
}

var clientState: ClientState = ClientState(SentState())
actual val globalState: GlobalState get() = clientState

fun Room.havePermission(permission: RoomPermission): Boolean {
    val myself = clientState.session.user
    return hasPermission(myself, permission)
}

@OptIn(ExperimentalSerializationApi::class)
actual val appConfig: AppConfig = confidoJSON.decodeFromDynamic(window.asDynamic().appConfig)
