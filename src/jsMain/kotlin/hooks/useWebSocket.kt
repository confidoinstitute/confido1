package hooks

import kotlinx.serialization.decodeFromString
import payloads.responses.WSResponse
import payloads.responses.WSError
import payloads.responses.WSErrorType
import payloads.responses.WSLoading
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
    var state by useState(WebSocketState.LOADING)
    val webSocket = useRef<WebSocket>(null)

    useEffect(state) {
        // If we are loading, create the websocket
        if (state == WebSocketState.LOADING) {
            val ws = WebSocket(webSocketUrl(address))
            ws.apply {
                onmessage = {
                    value = confidoJSON.decodeFromString(it.data.toString())
                    state = WebSocketState.DATA
                }
                onclose = {
                    state = WebSocketState.STALE
                    if (clearOnStale)
                        value = WSError(WSErrorType.DISCONNECTED, "Lost connection")
                    webSocket.current = null
                }
            }
            webSocket.current = ws
        }
        // If we are stale, try reloading later
        else if (state == WebSocketState.STALE) {
            setTimeout({ state = WebSocketState.LOADING }, retryDelay)
        }
    }

    // Close the websocket on unmount
    useEffectOnce {
        cleanup {
            webSocket.current?.apply {
                onclose = null
                close()
            }
        }
    }

    return value
}