package components.layout

import components.AppStateContext
import components.AppStateWebsocketProvider
import components.presenter.PresenterPage
import components.redesign.DefaultTheme
import components.redesign.ThemeProvider
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import csstype.*
import emotion.react.css
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.state.EmptyPV
import utils.webSocketUrl
import web.timers.setTimeout
import websockets.WebSocket

val PresenterLayout = FC<Props> {
    AppStateWebsocketProvider {
        ThemeProvider {
            theme = { _ -> DefaultTheme }
            LayoutModeContext.Provider {
                value = LayoutMode.DESKTOP
                PresenterLayoutInner {}
            }
        }
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

    div {
        css {
            height = 100.vh
            width = 100.vw
            margin = 0.px
            padding = 0.px
            overflow = Overflow.hidden
            display = Display.flex
            flexDirection = FlexDirection.column
        }
        PresenterPage { view = appState.session.presenterInfo?.view ?: EmptyPV }
    }
}