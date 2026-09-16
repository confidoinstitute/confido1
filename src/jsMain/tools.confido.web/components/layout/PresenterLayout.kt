package tools.confido.web.components.layout

import tools.confido.web.components.usePresenterInfo
import tools.confido.web.components.AppStateWebsocketProvider
import tools.confido.web.components.presenter.PresenterPage
import tools.confido.web.components.basic.ConnectionBanner
import tools.confido.web.components.DefaultTheme
import tools.confido.web.components.ThemeProvider
import tools.confido.web.components.layout.LayoutMode
import tools.confido.web.components.layout.LayoutModeContext
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import tools.confido.web.hooks.useSubscription
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
                ConnectionBanner {}
                PresenterLayoutInner {}
            }
        }
    }
}
val PresenterLayoutInner = FC<Props> {

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
        PresenterPage { view = usePresenterInfo()?.view ?: EmptyPV }
    }
}
