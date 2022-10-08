package components.layout

import kotlinx.browser.window
import kotlinx.js.timers.Timeout
import kotlinx.js.timers.clearInterval
import kotlinx.js.timers.setInterval
import kotlinx.js.timers.setTimeout
import kotlinx.serialization.decodeFromString
import org.w3c.dom.CloseEvent
import org.w3c.dom.WebSocket
import react.*
import tools.confido.serialization.confidoJSON
import tools.confido.utils.unixNow
import utils.webSocketUrl

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