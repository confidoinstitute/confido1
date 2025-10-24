package components.layout

import components.AppStateContext
import components.AppStateWebsocketProvider
import components.presenter.PresenterPage
import components.redesign.DefaultTheme
import components.redesign.ThemeProvider
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.state.EmptyPV
import utils.webSocketUrl
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.Overflow
import web.cssom.px
import web.cssom.vh
import web.cssom.vw
import web.events.EventHandler
import web.messaging.MessageEvent
import web.timers.setTimeout
import web.sockets.WebSocket

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
    val (appState, stale) = use(AppStateContext)!!

    fun startWebSocket() {
        val ws = WebSocket(webSocketUrl("/state/presenter/track"))

        ws.apply {
            onmessage = EventHandler { event: MessageEvent<*> ->
                console.log(event.data)
            }
            onclose = EventHandler {
                setTimeout(::startWebSocket, 5000)
            }
        }
        webSocket.current = ws
    }

    useEffectOnceWithCleanup {
        startWebSocket()
        onCleanup {
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
