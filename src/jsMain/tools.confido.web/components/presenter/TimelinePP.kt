package tools.confido.web.components.presenter

import emotion.react.css
import js.objects.unsafeJso as jso
import react.FC
import react.dom.html.ReactHTML.div
import tools.confido.payloads.responses.WSData
import tools.confido.refs.ref
import tools.confido.state.TimelinePV
import tools.confido.state.Topics
import tools.confido.web.components.QuestionPredictors
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.basic.sansSerif
import tools.confido.web.components.questions.predictions.GroupPredictionTimeline
import tools.confido.web.components.rooms.RoomContext
import tools.confido.web.components.useQuestion
import tools.confido.web.hooks.useSubscription
import web.cssom.*

/** How much the presenter blows up the question-page chart: the wrapper lays
 *  out at 1/ZOOM of the screen width and CSS zoom scales it back up, so fonts
 *  grow and mark density adapts automatically (SVG stays crisp). */
private const val ZOOM = 2.2

val TimelinePP = FC<PresenterPageProps<TimelinePV>> { props ->
    // Hooks precede the data-dependent early returns (React #310).
    val question = useQuestion(props.view.question)
    val rooms = (useSubscription(Topics.rooms()) as? WSData)?.data
    val room = rooms?.values?.firstOrNull { props.view.question in it.questions }
    if (question == null) return@FC

    Stack {
        css {
            gap = 30.pt
            width = 100.vw
            height = 100.vh
            padding = 2.5.vh
            alignItems = AlignItems.center
            justifyContent = JustifyContent.spaceBetween
            fontFamily = sansSerif
        }
        div {
            css {
                textAlign = TextAlign.center
                fontWeight = integer(600)
                fontSize = 7.vh
            }
            +question.name
        }
        div {
            // CSS zoom scales the content inside the box (the box itself keeps its
            // percentage width), so full width here = chart lays out at 1/ZOOM
            // logical px and renders across the whole screen.
            style = jso { asDynamic().zoom = ZOOM }
            css { width = 100.pct }
            // Room-wide markers come via RoomContext, like on the question page.
            RoomContext.Provider {
                value = room
                GroupPredictionTimeline {
                    this.question = question
                    resolution = question.resolution.takeIf { question.resolutionVisible }
                    presenter = true
                    windowStart = props.view.windowStart
                    windowEnd = props.view.windowEnd
                }
            }
        }
        Stack {
            direction = FlexDirection.row
            css {
                width = 100.pct
                justifyContent = JustifyContent.flexStart
                fontSize = 4.vh
            }
            div {
                QuestionPredictors { this.question = question.ref }
                +" people answered"
            }
        }
    }
}
