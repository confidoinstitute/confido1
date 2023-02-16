package hooks

import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import payloads.responses.*
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState
import tools.confido.serialization.confidoJSON
import utils.webSocketUrl
import web.timers.setTimeout
import websockets.WebSocket

enum class WebSocketState {
    LOADING,
    DATA,
    STALE,
}

inline fun <reified T> useWebSocket(address: String, retryDelay: Int = 5000, clearOnStale: Boolean = false): WSResponse<T> {
    var value by useState<WSResponse<T>>(WSLoading())

    useCoroutine(address) {
        value = WSLoading()
        while (true) {
            try {
                console.log("WS OPEN", address)
                Client.httpClient.webSocket(address) {
                    while (true) {
                        value = receiveDeserialized()
                    }
                }
            } catch (e: CancellationException) {
                console.log("WS CANCEL", address)
                throw e
            } catch (e: Exception) {
                console.log("WS ERROR", address, "\n", e.toString())
            }
            if (value is WSData && !clearOnStale) value = WSData(value.data!!, true)
            else value = WSLoading()
            delay(retryDelay.toLong())
        }
    }
    return value
}
