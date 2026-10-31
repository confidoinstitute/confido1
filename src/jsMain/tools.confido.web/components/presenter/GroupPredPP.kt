package tools.confido.web.components.presenter

import tools.confido.web.components.basic.Stack
import tools.confido.web.components.questions.predictions.NumericPredGraph
import tools.confido.web.components.basic.sansSerif
import tools.confido.web.components.basic.serif
import tools.confido.web.components.questions.predictions.BinaryPrediction
import web.cssom.*
import emotion.react.css
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.div
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.question.Prediction
import tools.confido.spaces.BinaryValue
import tools.confido.spaces.NumericValue
import web.window.window
import tools.confido.state.GroupPredPV
import tools.confido.state.Topics
import tools.confido.refs.ref
import tools.confido.web.components.QuestionPredictors
import tools.confido.web.components.useQuestion

val GroupPredPP = FC <PresenterPageProps<GroupPredPV>> { props ->
    // Hooks precede the data-dependent early returns: on a cold load the
    // subscriptions deliver after the first render, and the hook order must
    // not change between the loading and loaded renders (React #310).
    val question = useQuestion(props.view.question)
    val response = useSubscription(question?.let { Topics.groupPred(it.ref) })
    if (question == null) return@FC

    Stack {
        css {
            gap = 30.pt
            width = 100.vw
            height = 100.vh
            padding = 2.5.vh
            alignItems = AlignItems.center
            justifyContent = JustifyContent.spaceBetween
        }
        // Keyed: the graph and description are conditional on the data, and the
        // footer's predictor subscription must not be remounted by position.
        div {
            key = "title"
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
                key = "graph"
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
                    } else if (it is ContinuousProbabilityDistribution) {
                        // The same graph the question page uses, scaled up for the
                        // big screen (fonts and mark density follow fontScale).
                        NumericPredGraph {
                            space = it.space
                            dist = it
                            isGroup = true
                            interactive = false
                            graphHeight = window.innerHeight * 0.4
                            fontScale = 2.0
                            if (props.view.showResolution)
                                resolution = question.resolution as? NumericValue
                        }
                  }
                } ?: div {
                    css { fontSize = 6.vh }
                    +"Nobody has yet answered."
                }
            }
            if (response.data?.dist !is BinaryDistribution)
            div {
                key = "description"
                css { fontSize = 4.vh }
                response.data?.dist?.let{
                    +it.description
                }
            }
        }
        Stack {
            key = "footer"
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
