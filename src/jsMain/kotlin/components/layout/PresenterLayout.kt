package components.layout

import components.AppStateContext
import components.presenter.PresenterPage
import csstype.vh
import csstype.vw
import kotlinx.serialization.decodeFromString
import mui.system.sx
import react.*
import tools.confido.serialization.confidoJSON
import tools.confido.state.EmptyPV
import tools.confido.utils.unixNow
import utils.webSocketUrl
import web.timers.Timeout
import web.timers.clearInterval
import web.timers.setInterval
import web.timers.setTimeout
import websockets.WebSocket

val PresenterLayout = FC<Props> {
    val webSocket = useRef<WebSocket>(null)
    val (appState, stale) = useContext(AppStateContext)

    fun startWebSocket() {
        val ws = WebSocket(("ws://localhost:8080/presenter/track"))

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

    mui.system.Box {
        sx {
            height = 100.vh
            width = 100.vw
        }
        PresenterPage { view = appState.session.presenterInfo?.view ?: EmptyPV }
    }
}