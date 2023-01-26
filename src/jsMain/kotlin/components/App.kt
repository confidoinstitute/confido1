package components

import browser.document
import components.layout.*
import csstype.*
import kotlinx.js.jso
import mui.material.*
import mui.material.styles.PaletteColor
import mui.material.styles.createTheme
import mui.system.ThemeProvider
import react.*
import react.router.Route
import react.router.Routes
import react.router.dom.BrowserRouter
import tools.confido.state.SentState
import tools.confido.state.appConfig

val AppStateContext = createContext<ClientAppState>()
val LoginContext = createContext<Login>()


data class ClientAppState(val state: SentState, val stale: Boolean = false)
data class Login(val isLoggedIn: Boolean, private val changeState: (Boolean) -> Unit) {
    /** Change state to logged in. The session cookie must be set already. **/
    fun login() = changeState(true)
    /** Change state to logged out and remove the session cookie. **/
    fun logout() {
        document.cookie = "session=; Path=/; expires=Thu, 01 Jan 1970 00:00:01 GMT"
        changeState(false)
    }
}

val globalTheme = createTheme(
    jso {
        palette = jso {
            primary = jso<PaletteColor> {
                main = Color("#675491")
                light = Color("#9681c2")
                dark = Color("#3a2b63")
                contrastText = Color("#ffffff")
            }
            secondary = jso<PaletteColor> {
                main = Color("#55a3b5")
                light = Color("#88d4e7")
                dark = Color("#1c7485")
                contrastText = Color("#ffffff")
            }
        }
    }
)

val App = FC<Props> {
    // TODO: initial state from cookie
    // TODO: react to cookie change?
    val sessionCookieExists = document.cookie.contains("session")
    var isLoggedIn by useState(sessionCookieExists)

    CssBaseline {}
    LoginContext.Provider {
        value = Login(isLoggedIn) { isLoggedIn = it }

        val isDemo = appConfig.demoMode
        val layout = if (isLoggedIn) {
            RootLayout
        } else {
            NoUserLayout
        }

        ThemeProvider {
            this.theme = globalTheme
            GlobalErrorMessage {
                BrowserRouter {
                    Routes {
                        if (isDemo)
                            Route {
                                path = "/"
                                index = true
                                element = DemoLayout.create {}
                            }
                        Route {
                            path = "/*"
                            index = !isDemo
                            element = layout.create {
                                key = "layout"
                            }
                        }
                        Route {
                            path = "presenter"
                            element = PresenterLayout.create()
                        }
                    }
                }
            }
        }
    }
}