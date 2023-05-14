package components.layout

import components.AppStateContext
import components.AppStateWebsocketProvider
import components.presenter.PresenterPage
import csstype.vh
import csstype.vw
import mui.system.sx
import react.*
import tools.confido.state.EmptyPV
import utils.webSocketUrl
import web.timers.setTimeout
import websockets.WebSocket

val PresenterLayout = FC<Props> {
    AppStateWebsocketProvider {
        PresenterLayoutInner {}
    }
}
val PresenterLayoutInner = FC<Props> {
    val webSocket = useRef<WebSocket>(null)
    val (appState, stale) = useContext(AppStateContext)

    fun startWebSocket() {
        val ws = WebSocket(webSocketUrl("/state/presenter/track"))

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