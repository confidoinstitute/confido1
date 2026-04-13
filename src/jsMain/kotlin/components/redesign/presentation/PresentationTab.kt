package components.redesign.presentation

import Client
import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.Button
import components.redesign.forms.IconButton
import components.redesign.presenter.openPresenterWindow
import components.redesign.presenter.setPresenterView
import components.redesign.rooms.RoomContext
import components.redesign.rooms.RoomHeader
import emotion.react.css
import hooks.useCoroutineLock
import hooks.useWebSocket
import payloads.responses.WSData
import payloads.responses.WSError
import payloads.responses.WSLoading
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.refs.ref
import tools.confido.state.*
import web.cssom.*

val PresentationTab = FC<Props> {
    val (appState, stale) = use(AppStateContext)!!
    val room = use(RoomContext)!!
    val submitLock = useCoroutineLock()

    val ws = useWebSocket<PresentationWSData>("/state${room.urlPrefix}/presentation")

    when (ws) {
        is WSLoading -> {
            div {
                css {
                    padding = 20.px
                    fontFamily = sansSerif
                    color = Color("#888")
                }
                +"Loading presentation..."
            }
        }
        is WSError -> {
            div {
                css {
                    padding = 20.px
                    fontFamily = sansSerif
                    color = NamedColor.red
                }
                +ws.prettyMessage
            }
        }
        is WSData -> {
            val data = ws.data
            val isRunning = data.runState != null

            // Header with controls
            RoomHeader {
                Stack {
                    direction = FlexDirection.row
                    css {
                        flexGrow = number(1.0)
                        alignItems = AlignItems.center
                        marginLeft = 5.px
                        marginRight = 5.px
                        gap = 10.px
                        fontSize = 14.px
                        fontWeight = integer(500)
                        color = Color("#888")
                    }
                    span {
                        css { fontWeight = integer(600); color = Color("#333") }
                        +data.presentation.name
                    }
                    if (data.presentation.numSlides > 0) {
                        span { +"${data.presentation.numSlides} slides" }
                    }
                    span { +"${data.presentation.items.size} items" }
                    div { css { flexGrow = number(1.0) } }

                    if (isRunning) {
                        Button {
                            css {
                                margin = 0.px
                                padding = Padding(6.px, 16.px)
                                fontSize = 13.px
                                backgroundColor = Color("#e53935")
                            }
                            +"Stop"
                            onClick = {
                                submitLock {
                                    Client.sendData("${room.urlPrefix}/presentation/stop", "",
                                        onError = { showError(it) }) {}
                                }
                            }
                        }
                    } else {
                        if (data.presentation.items.isNotEmpty()) {
                            Button {
                                css {
                                    margin = 0.px
                                    padding = Padding(6.px, 16.px)
                                    fontSize = 13.px
                                    backgroundColor = Color("#4caf50")
                                }
                        +"Run"
                        onClick = {
                            submitLock {
                                Client.sendData("${room.urlPrefix}/presentation/start", "",
                                    onError = { showError(it) }) {
                                    // Set PresentationPV on user session and open presenter window
                                    setPresenterView(PresentationPV(room.ref))
                                    openPresenterWindow()
                                }
                            }
                        }
                            }
                        }
                        Button {
                            css {
                                margin = 0.px
                                padding = Padding(6.px, 16.px)
                                fontSize = 13.px
                                backgroundColor = Color("#1976d2")
                            }
                    +"Open presenter"
                        onClick = {
                            submitLock {
                                setPresenterView(PresentationPV(room.ref))
                                openPresenterWindow()
                            }
                        }
                        }
                    }
                }
                fullWidth = true
            }

            // Main content: editor or runner
            if (isRunning) {
                PresentationRunner {
                    this.data = data
                }
            } else {
                PresentationEditor {
                    this.data = data
                }
            }
        }
    }
}
