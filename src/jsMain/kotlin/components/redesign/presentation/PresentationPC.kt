package components.redesign.presentation

import Client
import components.presenter.PresenterControlsProps
import components.presenter.getPresenterControlsComponent
import components.redesign.basic.*
import components.redesign.forms.Button
import emotion.react.css
import hooks.useCoroutineLock
import hooks.useSubscription
import payloads.requests.NavigateDirection
import payloads.requests.NavigatePresentation
import payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import rooms.Room
import tools.confido.state.*
import utils.runCoroutine
import web.cssom.*
import tools.confido.state.Topics

private val NavButton = Button.withStyle {
    margin = 0.px
    padding = Padding(6.px, 14.px)
    fontSize = 13.px
}

/**
 * Control panel contents for [PresentationPV]: prev/next navigation, a hint
 * about the upcoming flow item, and the controls of the inner view currently
 * shown by the presentation (if that view type has controls registered).
 */
val PresentationPC = FC<PresenterControlsProps<PresentationPV>> { props ->
    val roomUrl = Room.urlPrefix(props.view.room.id)
    val ws = useSubscription<PresentationWSData>(Topics.presentation(props.view.room))
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

    fun navigate(dir: NavigateDirection) {
        submitLock {
            Client.sendData("$roomUrl/presentation/navigate", NavigatePresentation(dir, null),
                onError = { showError(it) }) {}
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
    // Controls for the inner view currently shown by the presentation, if any.
    // Changes override the run state's view until the next navigation step.
    runState.currentView?.let { inner ->
        getPresenterControlsComponent(inner)?.let { comp ->
            comp {
                this.view = inner
                this.disabled = props.disabled
                this.onChange = { newView ->
                    runCoroutine {
                        Client.sendData("$roomUrl/presentation/setview", newView,
                            onError = { showError(it) }) {}
                    }
                }
            }
        }
    }
}
