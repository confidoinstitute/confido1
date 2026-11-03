package tools.confido.web.components.presentation

import tools.confido.api.Api
import tools.confido.web.Client
import tools.confido.web.components.presenter.PresenterControlsProps
import tools.confido.web.components.presenter.getPresenterControlsComponent
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.Button
import tools.confido.web.components.forms.ButtonUnstyled
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.requests.NavigateDirection
import tools.confido.payloads.requests.NavigatePresentation
import tools.confido.payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.state.*
import tools.confido.web.components.presenter.setPresenterView
import tools.confido.web.utils.runCoroutine
import web.cssom.*
import tools.confido.state.Topics

private val NavButton = Button.withStyle {
    margin = 0.px
    padding = Padding(6.px, 14.px)
    fontSize = 13.px
}

/**
 * Control panel contents for [PresentationPV] — the pocket presentation remote:
 * prev/next navigation, the current and upcoming flow items (incl. presenter
 * notes, which only moderators see), and the controls of the inner view
 * currently shown by the presentation (if that view type has controls
 * registered).
 */
val PresentationPC = FC<PresenterControlsProps<PresentationPV>> { props ->
    val ws = useSubscription(Topics.presentation(props.view.room))
    val submitLock = useCoroutineLock()
    val data = (ws as? WSData)?.data
    val runState = data?.runState

    if (data == null || runState == null) {
        div {
            css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#888888") }
            +"The presentation is not running."
        }
        return@FC
    }
    val items = runState.expandedItems
    val current = items.getOrNull(runState.currentIndex)

    fun navigate(dir: NavigateDirection) {
        submitLock {
            Client.call(Api.presentationNavigate(props.view.room), NavigatePresentation(dir, null))
            // Re-assert the session's presenter view so navigating brings the
            // projector back to the presentation even after a manual view change
            // (parity with PresentationRunner and the keyboard-nav hook).
            setPresenterView(PresentationPV(props.view.room))
        }
    }

    Stack {
        direction = FlexDirection.row
        css {
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            gap = 8.px
        }
        NavButton {
            disabled = props.disabled || runState.currentIndex <= 0
            onClick = { navigate(NavigateDirection.PREV) }
            +"◀ Prev"
        }
        span {
            css {
                fontFamily = sansSerif
                fontSize = 13.px
                fontWeight = integer(600)
                minWidth = 50.px
                textAlign = TextAlign.center
            }
            +"${runState.currentIndex + 1} / ${items.size}"
        }
        NavButton {
            disabled = props.disabled || runState.currentIndex >= items.lastIndex
            onClick = { navigate(NavigateDirection.NEXT) }
            +"Next ▶"
        }
    }
    // Current step: presenter notes render in full (they exist for exactly this
    // surface), everything else as a one-line description.
    current?.let { cur ->
        if (cur is NoteItem && cur.text.isNotBlank()) {
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 13.px
                    fontWeight = if (cur.warning) integer(700) else integer(400)
                    color = if (cur.warning) Color("#b71c1c") else Color("#1b5e20")
                    backgroundColor = if (cur.warning) Color("#ffebee") else Color("#e8f5e9")
                    padding = Padding(4.px, 8.px)
                    borderRadius = 4.px
                }
                +((if (cur.warning) "⚠ " else "") + cur.text)
            }
        } else {
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 12.px
                    color = Color("#444444")
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                }
                +"Now: ${cur.shortDescribe(data.presentation)}"
            }
        }
    }
    items.getOrNull(runState.currentIndex + 1)?.let { next ->
        div {
            css {
                fontFamily = sansSerif
                fontSize = 12.px
                color = Color("#888888")
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                whiteSpace = WhiteSpace.nowrap
            }
            +"Next: ${next.shortDescribe(data.presentation)}"
        }
    }
    // The current step's view has been live-tweaked away from the script
    // (sticky for this run); offer the way back.
    if (current != null && current.id in runState.viewOverrides) {
        Stack {
            direction = FlexDirection.row
            css { alignItems = AlignItems.center; gap = 6.px }
            span {
                css {
                    fontSize = 11.px
                    fontFamily = sansSerif
                    color = Color("#7b1fa2")
                    backgroundColor = Color("#f3e5f5")
                    padding = Padding(1.px, 6.px)
                    borderRadius = 3.px
                }
                +"modified from script"
            }
            ButtonUnstyled {
                css {
                    fontSize = 11.px
                    fontFamily = sansSerif
                    color = Color("#6319FF")
                    cursor = Cursor.pointer
                    textDecoration = TextDecoration.underline
                }
                +"Revert"
                onClick = { submitLock { Client.call(Api.presentationRevertView(props.view.room)) } }
            }
        }
    }
    // Controls for the inner view currently shown by the presentation, if any.
    // Changes are stored as a per-step override: sticky when navigating away and
    // back, discarded by Revert or when the presentation stops.
    runState.currentView?.let { inner ->
        getPresenterControlsComponent(inner)?.let { comp ->
            comp {
                // Key by class: the mounted control must always match the view
                // class it receives (hook-order discipline).
                key = inner::class.simpleName
                this.view = inner
                this.disabled = props.disabled
                this.onChange = { newView ->
                    runCoroutine {
                        Client.call(Api.presentationSetView(props.view.room), newView)
                    }
                }
            }
        }
    }
}
