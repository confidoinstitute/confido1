package tools.confido.application.sessions

import kotlinx.coroutines.channels.Channel

/**
 * Short-lived session data. Will be lost upon server restart.
 */
class TransientData {
    val websocketRefreshChannel: Channel<Unit> = Channel()
}