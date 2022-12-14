package components.layout

import kotlinx.serialization.decodeFromString
import react.*
import tools.confido.serialization.confidoJSON
import tools.confido.utils.unixNow
import utils.webSocketUrl
import web.timers.Timeout
import web.timers.clearInterval
import web.timers.setInterval
import web.timers.setTimeout
import websockets.WebSocket

val PresenterLayout = FC<Props> {
    val webSocket = useRef<WebSocket>(null)

    fun startWebSocket() {
        val ws = WebSocket(("ws://localhost:8080/state_presenter"))

        fun ping() {
            ws.send(unixNow().toString())
        }

        ws.apply {
            onmessage = {
                console.log(it.data)
            }
            onclose = {
                setTimeout(::startWebSocket, 5000)
            }
        }
        webSocket.current = ws
    }

    useEffectOnce {
        startWebSocket()
        cleanup {
            webSocket.current?.close()
        }
    }

    +"This is presenter mode."
}