package tools.confido.web.components.presentation

import tools.confido.api.Api
import tools.confido.web.Client
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.Button
import tools.confido.web.components.forms.IconButton
import tools.confido.web.components.presenter.openPresenterWindow
import tools.confido.web.components.presenter.setPresenterView
import tools.confido.web.components.rooms.RoomContext
import tools.confido.web.components.rooms.RoomHeader
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import tools.confido.payloads.responses.WSError
import tools.confido.payloads.responses.WSLoading
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.refs.ref
import tools.confido.state.*
import web.cssom.*
import tools.confido.state.Topics

val PresentationTab = FC<Props> {
    val room = use(RoomContext)!!
    val submitLock = useCoroutineLock()

    val ws = useSubscription(Topics.presentation(room.ref))

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
            if (data.runState != null) {
                PresentationHeader {
                    presentation = data.presentation
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
                                Client.call(Api.presentationStop(room.ref))
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
                PresentationRunner {
                    this.data = data
                }
            } else {
                // The editor renders the header itself: its actions (upload,
                // save, run) live there so they stay visible while scrolling
                // a long flow (RoomHeader is sticky).
                PresentationEditor {
                    this.data = data
                }
            }
        }
    }
}

/**
 * The sticky presentation toolbar: name and counts on the left, the caller's
 * action buttons on the right.
 */
external interface PresentationHeaderProps : PropsWithChildren {
    var presentation: Presentation
}

val PresentationHeader = FC<PresentationHeaderProps> { props ->
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
                flexWrap = FlexWrap.wrap
            }
            span {
                css { fontWeight = integer(600); color = Color("#333") }
                +props.presentation.name
            }
            if (props.presentation.numSlides > 0) {
                span { +"${props.presentation.numSlides} slides" }
            }
            span { +"${props.presentation.items.size} items" }
            div { css { flexGrow = number(1.0) } }
            +props.children
        }
        fullWidth = true
    }
}
