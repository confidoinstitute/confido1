package components.layout

import components.AppStateContext
import components.AppStateWebsocketProvider
import components.presenter.PresenterPage
import components.redesign.DefaultTheme
import components.redesign.ThemeProvider
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import hooks.useSubscription
import react.*
import tools.confido.state.Topics
import react.dom.html.ReactHTML.div
import tools.confido.state.EmptyPV
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.Overflow
import web.cssom.px
import web.cssom.vh
import web.cssom.vw

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
    val (appState, stale) = use(AppStateContext)!!

    // Presence subscription: tells the server a presenter window is open.
    useSubscription(Topics.presenterTrack())

    Global {
        styles {
            "html, body" {
                margin = 0.px
                padding = 0.px
            }
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
        PresenterPage { view = appState.presenterInfo?.view ?: EmptyPV }
    }
}
