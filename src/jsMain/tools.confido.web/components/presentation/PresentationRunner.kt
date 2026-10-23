package tools.confido.web.components.presentation

import tools.confido.api.Api
import tools.confido.web.Client
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.Button
import tools.confido.web.components.forms.ButtonUnstyled
import tools.confido.web.components.presenter.setPresenterView
import tools.confido.web.components.rooms.RoomContext
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import tools.confido.web.hooks.usePresentationKeyboardNav
import tools.confido.payloads.requests.NavigateDirection
import tools.confido.payloads.requests.NavigatePresentation
import tools.confido.web.components.presenter.FlowActionDescription
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import tools.confido.refs.ref
import tools.confido.state.*
import web.cssom.*
import web.html.HTMLElement

external interface PresentationRunnerProps : Props {
    var data: PresentationWSData
}

val PresentationRunner = FC<PresentationRunnerProps> { props ->
    val room = use(RoomContext)!!
    val submitLock = useCoroutineLock()
    val listRef = useRef<web.html.HTMLElement>(null)
    val runState = props.data.runState ?: return@FC
    val items = runState.expandedItems
    val pres = props.data.presentation
    val currentItem = items.getOrNull(runState.currentIndex)

    fun navigate(dir: NavigateDirection, index: Int? = null) {
        submitLock {
            Client.call(Api.presentationNavigate(room.ref), NavigatePresentation(dir, index))
            // Ensure the session's presenter view points at this presentation,
            // so that even if the user manually changed it, navigating brings it back.
            setPresenterView(PresentationPV(room.ref))
        }
    }

    // Keyboard navigation with debounce (shared hook, also used in the presenter window)
    usePresentationKeyboardNav(room.id)

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
                    backgroundColor = flowItemBgColor(currentItem)
                    border = Border(2.px, LineStyle.solid, flowItemBadgeColor(currentItem))
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
                            src = pres.thumbUrl(currentItem.slideIndex)
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
                                FlowActionDescription { action = currentItem.action }
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
                                        Client.call(Api.presentationReexecute(room.ref))
                                    }
                                }
                            }
                        }
                    }
                    // Loops never survive expansion; defensive no-op.
                    is ForEachItem -> {}
                }
                // The step's view has been live-tweaked away from the script
                // (sticky for this run); offer the way back.
                if (currentItem.id in runState.viewOverrides) {
                    Stack {
                        direction = FlexDirection.row
                        css { alignItems = AlignItems.center; gap = 6.px; marginTop = 8.px }
                        span {
                            css {
                                fontSize = 11.px
                                color = Color("#7b1fa2")
                                backgroundColor = Color("#f3e5f5")
                                padding = Padding(1.px, 6.px)
                                borderRadius = 3.px
                            }
                            +"view modified from script"
                        }
                        ButtonUnstyled {
                            css {
                                fontSize = 11.px
                                color = Color("#6319FF")
                                cursor = Cursor.pointer
                                textDecoration = TextDecoration.underline
                            }
                            +"Revert"
                            onClick = { submitLock { Client.call(Api.presentationRevertView(room.ref)) } }
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
                            backgroundColor = if (item is ActionItem && item.id in runState.executedActionIds)
                                Color("#4caf50") else flowItemBadgeColor(item)
                            padding = Padding(0.px, 4.px)
                            borderRadius = 2.px
                            flexShrink = number(0.0)
                        }
                        +when {
                            item is ActionItem && item.id in runState.executedActionIds -> "✓"
                            else -> flowItemBadgeText(item).take(1)
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
                        when (item) {
                            is SlideItem -> +pres.slideLabels.getOrElse(item.slideIndex) { "Slide ${item.slideIndex + 1}" }
                            is ActionItem -> FlowActionDescription { action = item.action }
                            is NoteItem -> +item.text.ifEmpty { "(empty note)" }
                            is ForEachItem -> +item.shortDescribe(pres)
                        }
                    }
                }
            }
        }
    }
}
