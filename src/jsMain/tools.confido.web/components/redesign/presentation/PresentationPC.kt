package tools.confido.web.components.redesign.presentation

import tools.confido.api.Api
import tools.confido.web.Client
import tools.confido.web.components.presenter.PresenterControlsProps
import tools.confido.web.components.presenter.getPresenterControlsComponent
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.forms.Button
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.requests.NavigateDirection
import tools.confido.payloads.requests.NavigatePresentation
import tools.confido.payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.rooms.Room
import tools.confido.state.*
import tools.confido.web.utils.runCoroutine
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

    fun navigate(dir: NavigateDirection) {
        submitLock {
            Client.call(Api.presentationNavigate(props.view.room), NavigatePresentation(dir, null))
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
                        Client.call(Api.presentationSetView(props.view.room), newView)
                    }
                }
            }
        }
    }
}
