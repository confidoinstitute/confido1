package tools.confido.application.sessions

import kotlinx.coroutines.channels.Channel

class TransientData {
    val websocketRefreshChannel: Channel<Unit> = Channel()
}