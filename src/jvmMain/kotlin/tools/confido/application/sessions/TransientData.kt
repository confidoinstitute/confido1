package tools.confido.application.sessions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Short-lived session data. Will be lost upon server restart.
 */
class TransientData {
    val websocketRefreshChannel: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun refreshRunningWebsockets() {
        websocketRefreshChannel.update { !it }
    }
}