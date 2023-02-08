package components.layout

import browser.window
import components.AppStateContext
import components.ClientAppState
import components.LoginContext
import components.nouser.EmailLoginAlreadyLoggedIn
import components.profile.AdminView
import components.profile.UserSettings
import components.profile.VerifyToken
import components.rooms.NewRoom
import components.rooms.Room
import components.rooms.RoomInviteLoggedIn
import csstype.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToDynamic
import mui.material.*
import mui.system.*
import react.*
import react.dom.html.ReactHTML.main
import react.router.*
import tools.confido.serialization.confidoJSON
import tools.confido.state.ClientState
import tools.confido.state.SentState
import tools.confido.state.appConfig
import tools.confido.state.clientState
import utils.byTheme
import utils.themed
import utils.webSocketUrl
import web.location.location
import web.timers.*
import websockets.CloseEvent
import websockets.WebSocket

enum class State {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

private val AppStateWebsocketProvider = FC<PropsWithChildren> { props ->
    val loginState = useContext(LoginContext)
    var appState by useState<SentState?>(null)
    var stale by useState(false)
    val (_, setWebsocketState) = useState(State.DISCONNECTED)
    val webSocket = useRef<WebSocket>(null)

    fun startWebSocket() {
        setWebsocketState.invoke { current ->
            if (current != State.DISCONNECTED) return@invoke current

            // These versions are used to check compatibility with the server.
            val bundleVer = window.asDynamic().bundleVer as String
            val appConfigVer = window.asDynamic().appConfigVer as String

            val ws = WebSocket(webSocketUrl("/state?bundleVer=${bundleVer}&appConfigVer=${appConfigVer}"))
            ws.apply {
                onmessage = {
                    val decodedState = confidoJSON.decodeFromString<SentState>(it.data.toString())
                    clientState = ClientState(decodedState)
                    appState = decodedState
                    stale = false
                    setWebsocketState(State.CONNECTED)

                    try {
                        @OptIn(ExperimentalSerializationApi::class)
                        window.asDynamic().curState = confidoJSON.encodeToDynamic(decodedState) // for easy inspection in devtools
                    } catch (e: Exception) {}
                    @Suppress("RedundantUnitExpression")
                    Unit // This is not redundant, because assignment fails some weird type checks
                }
                onclose = {
                    console.log("Closed websocket")
                    setWebsocketState(State.DISCONNECTED)
                    stale = true
                    webSocket.current = null
                    (it as? CloseEvent)?.let { event ->
                        if (appConfig.devMode) console.log("AppState Websocket closed: code ${event.code}")
                        if (event.code == 4001) {
                            if (appConfig.devMode) console.log("AppState Websocket closed: incompatible frontend version")
                            // Incompatible frontend version
                            location.reload()
                        }
                        if (event.code == 3000) {
                            // Unauthorized
                            if (appConfig.devMode) console.log("AppState Websocket closed: unauthorized")
                            loginState.logout()
                        }
                    }
                }
            }
            webSocket.current = ws
            return@invoke State.CONNECTING
        }
    }

    useEffectOnce {
        val retryInterval = setInterval(::startWebSocket, 5000)
        startWebSocket()

        cleanup {
            clearInterval(retryInterval)
            webSocket.current?.apply {
                onclose = null
                close()
            }
        }
    }

    if (appState != null) {
        AppStateContext.Provider {
            value = ClientAppState(appState ?: error("No app state!"), stale)
            +props.children
        }
    } else {
        if (!stale) {
            LoadingLayout {}
        } else {
            NoStateLayout {
                this.stale = stale
            }
        }
    }
}

private val LoadingLayout = FC<Props> {
    RootAppBar {
        hasDrawer = false
        hasProfileMenu = false
    }
    Toolbar {}
    mui.system.Box {
        sx {
            margin = byTheme("auto")
            maxWidth = byTheme("lg")
        }
        NoStateLayout {}
    }
}

val RootLayout = FC<Props> {
    AppStateWebsocketProvider {
        RootLayoutInner {}
    }
}

private val RootLayoutInner = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    var drawerOpen by useState(false)

    val theme = mui.material.styles.useTheme<mui.material.styles.Theme>().breakpoints.up(permanentBreakpoint)
    val mediaMatch = useMediaQuery(theme)
    useEffect(mediaMatch) {
        drawerOpen = false
    }

    // Root element
    mui.system.Box {
        key = "rootBox"
        sx {
            display = Display.flex
            height = 100.vh
            alignItems = AlignItems.stretch
        }
        CssBaseline {}

        RootAppBar {
            key = "appbar"
            hasDrawer = true
            onDrawerOpen = { drawerOpen = true }
            isDisconnected = stale
            hasProfileMenu = true
        }

        Sidebar {
            key = "sidebar"
            permanent = mediaMatch
            isOpen = drawerOpen
            onClose = { drawerOpen = false }
        }
        mui.system.Box {
            key = "main"
            component = main
            sx {
                flexGrow = number(1.0)
                overflowX = Overflow.hidden
                padding = themed(1)
            }
            Toolbar {}
            mui.system.Box {
                sx {
                    margin = byTheme("auto")
                    maxWidth = byTheme("lg")
                }
                Routes {
                    Route {
                        index = true
                        path = "/"
                        this.element = Typography.create { +"Welcome to Confido!" }
                    }
                    Route {
                        path = "room/:roomID/*"
                        this.element = Room.create()
                    }
                    Route {
                        path = "room/:roomID/invite/:inviteToken"
                        this.element = RoomInviteLoggedIn.create()
                    }
                    Route {
                        path = "email_verify"
                        this.element = VerifyToken.create {
                            url = "/profile/email/verify"
                            failureTitle = "Email verification failed"
                            successTitle = "Email verification success"
                            failureText = "The verification link is expired or invalid."
                            successText = "Your email address has been successfully verified."
                        }
                    }
                    Route {
                        path = "password_reset"
                        this.element = VerifyToken.create {
                            url = "/profile/password/reset/finish"
                            failureTitle = "Password reset failed"
                            successTitle = "Password was reset"
                            failureText = "The link is expired or invalid."
                            successText = "Your password has been successfully reset. You can log in by e-mail only now."
                        }
                    }
                    Route {
                        path = "email_login"
                        this.element = EmailLoginAlreadyLoggedIn.create()
                    }
                    if (appState.session.user?.type?.isProper() == true) {
                        Route {
                            path = "new_room"
                            this.element = NewRoom.create()
                        }
                    }
                    Route {
                        path = "profile"
                        this.element = UserSettings.create()
                    }
                    if (appState.isAdmin()) {
                        Route {
                            path = "admin/users"
                            this.element = AdminView.create()
                        }
                    }
                }
            }
        }
    }
}