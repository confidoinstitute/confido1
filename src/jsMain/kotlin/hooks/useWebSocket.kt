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

/**
 * Connect to the Websocket channel at a given address and receive the continuous data.
 */
inline fun <reified T> useWebSocket(address: String, retryDelay: Int = 5000, clearOnStale: Boolean = false): WSResponse<T> {
    var value by useState<WSResponse<T>>(WSLoading())
    val fullAddr = if ("://" in address) address else webSocketUrl(address)

    useCoroutine(fullAddr) {
        value = WSLoading()
        while (true) {
            try {
                console.log("WS OPEN", fullAddr)
                Client.httpClient.webSocket(fullAddr) {
                    while (true) {
                        value = receiveDeserialized()
                    }
                }
            } catch (e: CancellationException) {
                console.log("WS CANCEL", fullAddr)
                throw e
            } catch (e: Exception) {
                console.log("WS ERROR", fullAddr, "\n", e.toString())
            }
            if (value is WSData && !clearOnStale) value = WSData(value.data!!, true)
            else value = WSLoading()
            delay(retryDelay.toLong())
        }
    }
    return value
}
