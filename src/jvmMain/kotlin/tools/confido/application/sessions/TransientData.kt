package tools.confido.application.sessions

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Short-lived session data. Will be lost upon server restart.
 */
class TransientData {
    val websocketRefreshChannel: MutableStateFlow<Boolean> = MutableStateFlow(false)
}