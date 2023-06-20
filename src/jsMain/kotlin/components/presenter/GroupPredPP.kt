package components.presenter

import components.DistributionPlot
import components.redesign.basic.serif
import components.redesign.questions.predictions.BinaryPrediction
import csstype.*
<<<<<<< HEAD
import dom.html.HTML.div
import dom.html.HTML.h3
import dom.html.HTML.h4
import dom.html.HTMLDivElement
=======
>>>>>>> 3834dda (Presenter: show resolution for binary question)
import emotion.react.css
import hooks.useElementSize
import hooks.useWebSocket
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.Box
import mui.system.responsive
import mui.system.sx
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
        sx {
            gap = 30.pt
            width = 100.pct
            height = 100.pct
            padding = themed(5)
            alignItems = AlignItems.center
            justifyContent = JustifyContent.spaceBetween
        }
        div {
            css {
                fontFamily = serif
                textAlign = TextAlign.center
                fontSize = 7.vh
                padding = 0.px
                margin = 0.px
            }
            +question.name
        }
        if (response is WSData) {
            Box {
                sx {
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
                } ?: Typography {
                    variant = TypographyVariant.h4
                    +"Nobody has yet answered."
                }
            }
            if (response.data?.dist !is BinaryDistribution)
            Typography {
                variant = TypographyVariant.h2
                response.data?.dist?.let{
                    +it.description
                }
            }
        }
        Stack {
            direction = responsive(StackDirection.row)
            sx {
                width = 100.pct
                justifyContent = JustifyContent.flexStart
            }
            Typography {
                variant = TypographyVariant.h6
                +"${question.numPredictors} people answered"
            }
        }
    }
}
