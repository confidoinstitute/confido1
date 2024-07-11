package components.presenter

import components.DistributionPlot
import components.redesign.basic.Stack
import components.redesign.basic.sansSerif
import components.redesign.basic.serif
import components.redesign.questions.predictions.BinaryPrediction
import csstype.*
import dom.html.HTML.div
import dom.html.HTML.h3
import dom.html.HTML.h4
import dom.html.HTMLDivElement
import emotion.react.css
import hooks.useElementSize
import hooks.useWebSocket
import payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.div
import tools.confido.distributions.BinaryDistribution
import tools.confido.question.Prediction
import tools.confido.refs.deref
import tools.confido.spaces.BinaryValue
import tools.confido.state.GroupPredPV
import utils.themed

val GroupPredPP = FC <PresenterPageProps<GroupPredPV>> { props ->
    val question = props.view.question.deref() ?: return@FC
    val response = useWebSocket<Prediction?>("/state${question.urlPrefix}/group_pred")

    Stack {
        css {
            gap = 30.pt
            width = 100.vw
            height = 100.vh
            padding = 2.5.vh
            alignItems = AlignItems.center
            justifyContent = JustifyContent.spaceBetween
        }
        div {
            css {
                fontFamily = sansSerif
                textAlign = TextAlign.center
                fontWeight = integer(600)
                fontSize = 7.vh
                padding = 0.px
                margin = 0.px
            }
            +question.name
        }
        if (response is WSData) {
            div {
                css {
                    width = 100.pct
                    flexGrow = number(1.0)
                }
                response.data?.dist?.let {
                    if (it is BinaryDistribution) {
                        BinaryPrediction {
                            baseHeight = 50.vh
                            dist = it
                            isGroup = true
                            interactive = false
                            if (props.view.showResolution && question.resolution != null)
                                resolution = question.resolution as? BinaryValue?
                        }
                    } else {
                    DistributionPlot {
                        distribution = it
                        fontSize = 32.0
                        resolutionLine = if (props.view.showResolution) question.resolution?.value as? Double else null
                    }
                  }
                } ?: div {
                    css { fontSize = 6.vh }
                    +"Nobody has yet answered."
                }
            }
            if (response.data?.dist !is BinaryDistribution)
            div {
                css { fontSize = 4.vh }
                response.data?.dist?.let{
                    +it.description
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
                +"${question.numPredictors} people answered"
            }
        }
    }
}
