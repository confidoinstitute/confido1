package components.redesign.presentation

import Client
import components.redesign.basic.*
import components.redesign.forms.Button
import components.redesign.rooms.RoomContext
import emotion.react.css
import hooks.useCoroutineLock
import payloads.requests.NavigateDirection
import payloads.requests.NavigatePresentation
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import tools.confido.state.*
import web.cssom.*
import web.dom.document
import web.events.Event
import web.events.EventType
import web.events.addEventHandler
import web.events.removeEventListener
import web.html.HTMLElement

external interface PresentationRunnerProps : Props {
    var data: PresentationWSData
}

val PresentationRunner = FC<PresentationRunnerProps> { props ->
    val room = use(RoomContext)!!
    val submitLock = useCoroutineLock()
    val runState = props.data.runState ?: return@FC
    val items = props.data.presentation.items
    val pres = props.data.presentation
    val currentItem = items.getOrNull(runState.currentIndex)
    val listRef = useRef<web.html.HTMLElement>(null)

    fun navigate(dir: NavigateDirection, index: Int? = null) {
        submitLock {
            Client.sendData("${room.urlPrefix}/presentation/navigate",
                NavigatePresentation(dir, index),
                onError = { showError(it) }) {}
        }
    }

    // Keyboard navigation
    useEffectOnceWithCleanup {
        val handler = { e: Event ->
            val ke = e.asDynamic()
            val key = ke.key as? String ?: ""
            // Don't capture if user is typing in an input
            val tagName = (ke.target?.tagName as? String)?.uppercase() ?: ""
            if (tagName != "INPUT" && tagName != "TEXTAREA") {
                when (key) {
                    "ArrowRight", "ArrowDown", " ", "PageDown" -> {
                        navigate(NavigateDirection.NEXT)
                        e.preventDefault()
                    }
                    "ArrowLeft", "ArrowUp", "PageUp" -> {
                        navigate(NavigateDirection.PREV)
                        e.preventDefault()
                    }
                }
            }
        }
        document.addEventHandler(EventType("keydown"), handler)
        onCleanup { document.removeEventListener(EventType<Event>("keydown"), handler) }
    }

    // Scroll current item into view
    useEffect(runState.currentIndex) {
        listRef.current?.querySelector("[data-current=true]")?.asDynamic()?.scrollIntoView(
            js("({block: 'nearest', behavior: 'smooth'})")
        )
    }

    Stack {
        css {
            padding = 15.px
            gap = 10.px
            height = 100.pct
        }

        // Navigation bar
        Stack {
            direction = FlexDirection.row
            css {
                gap = 10.px
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
            }
            Button {
                css {
                    margin = 0.px
                    padding = Padding(8.px, 20.px)
                    fontSize = 14.px
                }
                disabled = runState.currentIndex <= 0
                +"◀ Prev"
                onClick = { navigate(NavigateDirection.PREV) }
            }
            span {
                css {
                    fontFamily = sansSerif
                    fontSize = 16.px
                    fontWeight = integer(600)
                    minWidth = 80.px
                    textAlign = TextAlign.center
                }
                +"${runState.currentIndex + 1} / ${items.size}"
            }
            Button {
                css {
                    margin = 0.px
                    padding = Padding(8.px, 20.px)
                    fontSize = 14.px
                }
                disabled = runState.currentIndex >= items.lastIndex
                +"Next ▶"
                onClick = { navigate(NavigateDirection.NEXT) }
            }
        }

        // Current item detail panel
        if (currentItem != null) {
            div {
                css {
                    padding = 15.px
                    borderRadius = 8.px
                    fontFamily = sansSerif
                    backgroundColor = when (currentItem) {
                        is NoteItem -> if (currentItem.warning) Color("#ffcdd2") else Color("#c8e6c9")
                        is SlideItem -> Color("#f5f5f5")
                        is ActionItem -> Color("#fff3e0")
                        is PresenterViewItem -> Color("#e3f2fd")
                    }
                    border = Border(2.px, LineStyle.solid, when (currentItem) {
                        is NoteItem -> if (currentItem.warning) Color("#e53935") else Color("#4caf50")
                        is SlideItem -> Color("#bdbdbd")
                        is ActionItem -> Color("#e65100")
                        is PresenterViewItem -> Color("#1565c0")
                    })
                }
                when (currentItem) {
                    is NoteItem -> {
                        div {
                            css {
                                fontSize = if (currentItem.warning) 20.px else 16.px
                                fontWeight = if (currentItem.warning) integer(700) else integer(400)
                                color = if (currentItem.warning) Color("#b71c1c") else Color("#1b5e20")
                            }
                            if (currentItem.warning) {
                                span { +"⚠ " }
                            }
                            +currentItem.text
                        }
                    }
                    is SlideItem -> {
                        val label = pres.slideLabels.getOrElse(currentItem.slideIndex) {
                            "Slide ${currentItem.slideIndex + 1}"
                        }
                        div {
                            css { fontSize = 14.px; color = Color("#666"); marginBottom = 8.px }
                            +label
                        }
                        img {
                            src = pres.slideUrl(currentItem.slideIndex)
                            css {
                                maxWidth = 400.px
                                maxHeight = 250.px
                                borderRadius = 4.px
                                border = Border(1.px, LineStyle.solid, Color("#ddd"))
                            }
                        }
                    }
                    is ActionItem -> {
                        val executed = currentItem.id in runState.executedActionIds
                        val justExecuted = currentItem.id == runState.lastExecutedId
                        val skipped = executed && !justExecuted
                        div {
                            css {
                                fontSize = 14.px
                                display = Display.flex
                                alignItems = AlignItems.center
                                gap = 8.px
                            }
                            span {
                                css { fontSize = 16.px }
                                +(when {
                                    justExecuted -> "✓"
                                    skipped -> "⏭"
                                    else -> "⏳"
                                })
                            }
                            span {
                                +currentItem.action.describe()
                            }
                            when {
                                justExecuted -> span {
                                    css { color = Color("#4caf50"); fontSize = 12.px }
                                    +"(executed)"
                                }
                                skipped -> span {
                                    css { color = Color("#ff8f00"); fontSize = 12.px; fontStyle = FontStyle.italic }
                                    +"(skipped re-execution)"
                                }
                            }
                        }
                        if (skipped) {
                            Button {
                                css {
                                    margin = 0.px
                                    marginTop = 8.px
                                    padding = Padding(6.px, 16.px)
                                    fontSize = 13.px
                                    backgroundColor = Color("#e65100")
                                }
                                +"Re-execute"
                                onClick = {
                                    submitLock {
                                        Client.sendData("${room.urlPrefix}/presentation/reexecute", "",
                                            onError = { showError(it) }) {}
                                    }
                                }
                            }
                        }
                    }
                    is PresenterViewItem -> {
                        div {
                            css { fontSize = 14.px }
                            +"Showing: ${currentItem.view.describe()}"
                        }
                    }
                }
            }
        }

        // Compact flow list
        div {
            ref = listRef
            css {
                flexGrow = number(1.0)
                overflowY = Auto.auto
                border = Border(1.px, LineStyle.solid, Color("#e0e0e0"))
                borderRadius = 4.px
            }
            items.forEachIndexed { index, item ->
                val isCurrent = index == runState.currentIndex
                val isPast = index < runState.currentIndex
                div {
                    asDynamic()["data-current"] = isCurrent.toString()
                    css {
                        display = Display.flex
                        flexDirection = FlexDirection.row
                        alignItems = AlignItems.center
                        gap = 8.px
                        padding = Padding(4.px, 10.px)
                        fontFamily = sansSerif
                        fontSize = 12.px
                        cursor = Cursor.pointer
                        if (isCurrent) {
                            backgroundColor = Color("#bbdefb")
                            fontWeight = integer(600)
                        } else if (isPast) {
                            backgroundColor = Color("#fafafa")
                            color = Color("#aaa")
                        }
                        "&:hover" {
                            backgroundColor = if (isCurrent) Color("#90caf9") else Color("#f0f0f0")
                        }
                        borderBottom = Border(1.px, LineStyle.solid, Color("#eee"))
                    }
                    onClick = { navigate(NavigateDirection.GOTO, index) }

                    // Index number
                    span {
                        css {
                            width = 30.px
                            textAlign = TextAlign.right
                            color = Color("#999")
                            flexShrink = number(0.0)
                        }
                        +"${index + 1}"
                    }

                    // Type indicator
                    span {
                        css {
                            fontSize = 9.px
                            fontWeight = integer(600)
                            color = NamedColor.white
                            backgroundColor = when (item) {
                                is SlideItem -> Color("#757575")
                                is ActionItem -> Color(if (item.id in runState.executedActionIds) "#4caf50" else "#e65100")
                                is NoteItem -> if (item.warning) Color("#c62828") else Color("#2e7d32")
                                is PresenterViewItem -> Color("#1565c0")
                            }
                            padding = Padding(0.px, 4.px)
                            borderRadius = 2.px
                            flexShrink = number(0.0)
                        }
                        +when (item) {
                            is SlideItem -> "S"
                            is ActionItem -> if (item.id in runState.executedActionIds) "✓" else "A"
                            is NoteItem -> if (item.warning) "⚠" else "N"
                            is PresenterViewItem -> "V"
                        }
                    }

                    // Description
                    span {
                        css {
                            flexGrow = number(1.0)
                            overflow = Overflow.hidden
                            textOverflow = TextOverflow.ellipsis
                            whiteSpace = WhiteSpace.nowrap
                        }
                        +when (item) {
                            is SlideItem -> pres.slideLabels.getOrElse(item.slideIndex) { "Slide ${item.slideIndex + 1}" }
                            is ActionItem -> item.action.describe()
                            is NoteItem -> item.text.ifEmpty { "(empty note)" }
                            is PresenterViewItem -> item.view.describe()
                        }
                    }
                }
            }
        }
    }
}
