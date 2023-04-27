package components.presenter

import components.DistributionPlot
import components.GroupPredictions
import components.redesign.questions.predictions.BinaryPrediction
import csstype.*
import dom.html.HTML.div
import dom.html.HTML.h4
import dom.html.HTMLDivElement
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
        Typography {
            variant = TypographyVariant.h3
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
                        }
                    } else {
                        DistributionPlot {
                            distribution = it
                            fontSize = 32.0
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
