package components.redesign.questions

import components.redesign.basic.Stack
import csstype.*
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.question.Prediction
import tools.confido.utils.formatPercent

external interface MyPredictionDescriptionProps : Props {
    var resolved: Boolean
    var prediction: Prediction?
}

external interface GroupPredictionDescriptionProps : Props {
    var resolved: Boolean
    var prediction: Prediction?
    var myPredictionExists: Boolean
    var numPredictors: Int
}

val predictorCountColor = Color("#FF6B00")
val yesColor = Color("#00CC2E")
val noColor = Color("#FF5555")


val GroupPredictionDescription = FC<GroupPredictionDescriptionProps> { props ->
    Stack {
        css {
            padding = Padding(25.px, 15.px)
            gap = 10.px
            color = Color("#222222")
            fontWeight = integer(500)
            fontFamily = FontFamily.sansSerif
            fontSize = 15.px
            lineHeight = 18.px
        }
        ReactHTML.div {
            if (!props.resolved) {
                +"So far, "
            }
            if (props.numPredictors == 0) {
                ReactHTML.b {
                    css { this.color = predictorCountColor }
                    +"nobody "
                }
                +"added an estimate."
            } else if (props.numPredictors == 1 && props.myPredictionExists) {
                if (props.resolved) {
                    +"Only"
                } else {
                    +"only "
                }
                ReactHTML.b {
                    css { this.color = predictorCountColor }
                    +"you "
                }
                +"added an estimate."
            } else {
                ReactHTML.b {
                    css { this.color = predictorCountColor }
                    +"${props.numPredictors} "
                }
                if (props.numPredictors > 1) {
                    +"people added at least one estimate."
                } else {
                    +"person added at least one estimate."
                }
            }
        }
        when (val dist = props.prediction?.dist) {
            is BinaryDistribution -> {
                var optionColor = noColor
                var answer = "No"
                var prob = dist.noProb
                if (dist.yesProb > 0.5) {
                    optionColor = yesColor
                    answer = "Yes"
                    prob = dist.yesProb
                }
                ReactHTML.div {
                    if (props.resolved) {
                        +"The group thought there is a "
                    } else {
                        +"The group thinks there is a "
                    }
                    ReactHTML.b {
                        css { this.color = optionColor }
                        +"${formatPercent(prob, space = false)} "
                    }
                    if (props.resolved) {
                        +"chance that the answer would be "
                    } else {
                        +"chance that the answer is "
                    }
                    ReactHTML.b {
                        css { this.color = optionColor }
                        +answer
                    }
                    +"."
                }
            }

            is ContinuousProbabilityDistribution -> {
                val medianColor = Color("#00CC2E")
                val confidenceColor = Color("#00C2FF")
                val rangeColor = Color("#0066FF")
                ReactHTML.div {
                    if (props.resolved) {
                        +"The median estimate of the answer was "
                    } else {
                        +"The median estimate of the answer is "
                    }
                    ReactHTML.b {
                        css { this.color = medianColor }
                        +dist.space.formatValue(dist.median)
                    }
                    +"."
                }

                val confidence = 0.8
                val confidenceInterval = dist.confidenceInterval(confidence)
                ReactHTML.div {
                    if (props.resolved) {
                        +"The group believed with "
                    } else {
                        +"The group believes with "
                    }
                    ReactHTML.b {
                        css { this.color = confidenceColor }
                        +"${formatPercent(confidence, space = false)} "
                    }
                    if (props.resolved) {
                        +"confidence that the answer would be between "
                    } else {
                        +"confidence that the answer is between "
                    }
                    ReactHTML.b {
                        css { this.color = rangeColor }
                        +"${dist.space.formatValue(confidenceInterval.start)} "
                    }
                    +"and "
                    ReactHTML.b {
                        css { this.color = rangeColor }
                        +dist.space.formatValue(confidenceInterval.endInclusive)
                    }
                    +"."
                }
            }

            else -> {
            }
        }
    }
}

val MyPredictionDescription = FC<MyPredictionDescriptionProps> { props ->
    Stack {
        css {
            padding = Padding(25.px, 15.px)
            gap = 10.px
            color = Color("#222222")
            fontWeight = integer(500)
            fontFamily = FontFamily.sansSerif
            fontSize = 15.px
            lineHeight = 18.px
        }
        when (val dist = props.prediction?.dist) {
            is BinaryDistribution -> {
                var optionColor = noColor
                var answer = "No"
                var prob = dist.noProb
                if (dist.yesProb > 0.5) {
                    optionColor = yesColor
                    answer = "Yes"
                    prob = dist.yesProb
                }
                ReactHTML.div {
                    if (props.resolved) {
                        +"You predicted there was a "
                    } else {
                        +"You think there is a "
                    }
                    ReactHTML.b {
                        css { this.color = optionColor }
                        +"${formatPercent(prob, space = false)} "
                    }
                    if (props.resolved) {
                        +"chance that the answer would be "
                    } else {
                        +"chance that the answer is "
                    }
                    ReactHTML.b {
                        css { this.color = optionColor }
                        +answer
                    }
                    +"."
                }
            }

            is ContinuousProbabilityDistribution -> {
                val medianColor = Color("#00CC2E")
                val confidenceColor = Color("#00C2FF")
                val rangeColor = Color("#0066FF")
                ReactHTML.div {
                    if (props.resolved) {
                        +"You thought that the most probable answer would be around "
                    } else {
                        +"You think that the most probable answer is around "
                    }
                    ReactHTML.b {
                        css { this.color = medianColor }
                        +dist.space.formatValue(dist.median)
                    }
                    +"."
                }

                val confidence = 0.8
                val confidenceInterval = dist.confidenceInterval(confidence)
                ReactHTML.div {
                    if (props.resolved) {
                        +"You were "
                    } else {
                        +"You are "
                    }
                    ReactHTML.b {
                        css { this.color = confidenceColor }
                        +"${formatPercent(confidence, space = false)} "
                    }
                    if (props.resolved) {
                        +"confident that the answer would be between "
                    } else {
                        +"confident that the answer is between "
                    }
                    ReactHTML.b {
                        css { this.color = rangeColor }
                        +"${dist.space.formatValue(confidenceInterval.start)} "
                    }
                    +"and "
                    ReactHTML.b {
                        css { this.color = rangeColor }
                        +dist.space.formatValue(confidenceInterval.endInclusive)
                    }
                    +"."
                }
            }

            else -> {
            }
        }
    }
}

