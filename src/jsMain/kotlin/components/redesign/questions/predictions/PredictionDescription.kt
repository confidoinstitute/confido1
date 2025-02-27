package components.redesign.questions.predictions

import BinaryHistogram
import components.redesign.basic.MainPalette
import components.redesign.basic.Stack
import components.redesign.basic.sansSerif
import components.redesign.questions.dialog.ExactEstimateDialog
import csstype.Color
import csstype.Padding
import csstype.integer
import csstype.px
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.useState
import tools.confido.distributions.*
import tools.confido.extensions.ClientExtension
import tools.confido.question.Prediction
import tools.confido.question.PredictionTerminology
import tools.confido.question.Question
import tools.confido.utils.formatPercent

external interface MyPredictionDescriptionProps : Props {
    var resolved: Boolean
    var dist: ProbabilityDistribution?
    var question: Question?
}

external interface GroupPredictionDescriptionProps : Props {
    var resolved: Boolean
    var prediction: Prediction?
    var myPredictionExists: Boolean
    var numPredictors: Int
    var predictionTerminology: PredictionTerminology
}

external interface BinaryHistogramDescriptionProps : Props {
    var resolved: Boolean
    var binaryHistogram: BinaryHistogram
    var predictionTerminology: PredictionTerminology
}

val predictorCountColor = Color("#000000")
val yesColor = Color("#00CC2E")
val noColor = Color("#FF5555")
val medianColor = Color("#2AE6C9")
val meanColor = Color("#00C3E9")

val descriptionClass = emotion.css.ClassName {
    padding = Padding(25.px, 15.px)
    gap = 10.px
    color = Color("#222222")
    fontWeight = integer(500)
    fontFamily = sansSerif
    fontSize = 15.px
    lineHeight = 18.px
}

val GroupPredictionDescription = FC<GroupPredictionDescriptionProps> { props ->
    val predTerm = props.predictionTerminology
    Stack {
        css(descriptionClass) {}
        div {
            if (!props.resolved) {
                +"So far, "
            }
            if (props.numPredictors == 0) {
                b {
                    css { this.color = predictorCountColor }
                    if (props.resolved) {
                        +"Nobody "
                    } else {
                        +"nobody "
                    }
                }
                +"added ${predTerm.aTerm}."
            } else if (props.numPredictors == 1 && props.myPredictionExists) {
                if (props.resolved) {
                    +"Only "
                } else {
                    +"only "
                }
                b {
                    css { this.color = predictorCountColor }
                    +"you "
                }
                +"added ${predTerm.aTerm}."
            } else {
                b {
                    css { this.color = predictorCountColor }
                    +"${props.numPredictors} "
                }
                if (props.numPredictors > 1) {
                    +"people added at least one ${predTerm.term}."
                } else {
                    +"person added at least one ${predTerm.term}."
                }
            }
        }
        when (val dist = props.prediction?.dist) {
            is BinaryDistribution -> {
                var optionColor = binaryColors[dist.likelyOutcome]
                val prob = dist.likelyOutcomeProb
                div {
                    if (props.resolved) {
                        +"The group thought there was a "
                    } else {
                        +"The group thinks there is a "
                    }
                    b {
                        css { this.color = optionColor }
                        +"${formatPercent(prob, space = false)} "
                    }
                    if (props.resolved) {
                        +"chance that the answer would be "
                    } else {
                        +"chance that the answer is "
                    }
                    yesNoColored(dist.likelyOutcome)
                    +"."
                }
            }

            is ContinuousProbabilityDistribution -> {
                val confidenceColor = Color("#00C2FF")
                val rangeColor = Color("#0066FF")
                div {
                    if (props.resolved) {
                        +"The median estimate of the answer was "
                    } else {
                        +"The median estimate of the answer is "
                    }
                    b {
                        css { this.color = MainPalette.center.color }
                        +dist.space.formatValue(dist.median)
                    }
                    +"."
                }

                val confidence = 0.8
                val confidenceInterval = dist.confidenceInterval(confidence, preferredCenter = dist.median)
                div {
                    if (props.resolved) {
                        +"The group believed with "
                    } else {
                        +"The group believes with "
                    }
                    b {
                        css { this.color = confidenceColor }
                        +"${formatPercent(confidence, space = false)} "
                    }
                    if (props.resolved) {
                        +"confidence that the answer would be between "
                    } else {
                        +"confidence that the answer is between "
                    }
                    b {
                        css { this.color = rangeColor }
                        +"${dist.space.formatValue(confidenceInterval.start)} "
                    }
                    +"and "
                    b {
                        css { this.color = rangeColor }
                        +dist.space.formatValue(confidenceInterval.endInclusive)
                    }
                    +"."
                }
            }
        }
    }
}

fun ChildrenBuilder.yesNoColored(yn: Boolean) {
    ReactHTML.b {
        css { this.color = binaryColors[yn] }
        +(if (yn) "Yes" else "No")
    }
}

val BinaryHistogramDescription = FC<BinaryHistogramDescriptionProps> { props ->
    val predictors = props.binaryHistogram.bins.sumOf { it.count }

    val predTerm = props.predictionTerminology
    Stack {
        css(descriptionClass) {}
        div {
            if (!props.resolved) {
                +"So far, "
            }
            if (predictors == 0) {
                b {
                    css { this.color = predictorCountColor }
                    if (props.resolved) {
                        +"Nobody "
                    } else {
                        +"nobody "
                    }
                }
                +"added ${predTerm.aTerm}."
            } else {
                b {
                    css { this.color = predictorCountColor }
                    +"$predictors "
                }
                if (predictors > 1) {
                    +"people added at least one ${predTerm.term}."
                } else {
                    +"person added at least one ${predTerm.term}."
                }
            }
        }
        props.binaryHistogram.median?.let { median ->
            div {
                +"The median ${predTerm.term} of the group "
                if (props.resolved) {
                    +"was a "
                } else {
                    +"is a "
                }
                b {
                    css { this.color = medianColor }
                    +"${formatPercent(median, space = false)} "
                }
                +"chance that the answer "
                if (props.resolved) {
                    +"would be "
                } else {
                    +"is "
                }
                b {
                    css { this.color = yesColor }
                    +"Yes"
                }
                +"."
            }
        }

        props.binaryHistogram.mean?.let { mean ->
            div {
                +"The average ${predTerm.term} of the group "
                if (props.resolved) {
                    +"was a "
                } else {
                    +"is a "
                }
                b {
                    css { this.color = meanColor }
                    +"${formatPercent(mean, space = false)} "
                }
                +"chance that the answer "
                if (props.resolved) {
                    +"would be "
                } else {
                    +"is "
                }
                b {
                    css { this.color = yesColor }
                    +"Yes"
                }
                +"."
            }
        }
    }
}


val MyPredictionDescription = FC<MyPredictionDescriptionProps> { props ->
    var exactOpen by useState(false)
    if (props.question != null)
    ExactEstimateDialog {
        question = props.question!!
        open = exactOpen
        onClose = {exactOpen = false}
    }
    val hideDefault = ClientExtension.enabled.any { it.hideDefaultPredictionDesc(props.dist, props.question, false) }
    Stack {
        css(descriptionClass) {}
        if (!hideDefault)
        when (val dist = props.dist) {
            is BinaryDistribution -> {
                val prob = dist.likelyOutcomeProb
                val optionColor = binaryColors[dist.likelyOutcome]
                ReactHTML.div {
                    if (props.resolved) {
                        +"You were "
                    } else {
                        +"You are "
                    }
                    ReactHTML.b {
                        css { this.color = optionColor }
                        +"${formatPercent(prob, space = false, decimals = if (prob >= 0.99 && prob < 1) 2 else 0)} "
                    }
                    if (props.resolved) {
                        +"confident that the answer would be "
                    } else {
                        +"confident that the answer is "
                    }
                    yesNoColored(dist.likelyOutcome)
                    +"."
                }
                if (prob == 1.0)
                ReactHTML.div {
                    +"This means "
                    b { +"absolute certainty" }
                    +" (i.e., more certain than the sun rising tomorrow). "
                    if (props.question != null && props.question!!.open && !props.resolved) {
                            +"If you'd like to enter a high but non-absolute confidence (e.g. "
                        +"99.98%), you can do so "
                        a {
                            +"numerically"
                            onClick = { exactOpen = true }
                        }
                        +"."
                    }
                }
            }

            is ContinuousProbabilityDistribution -> {
                val confidenceColor = Color("#00C2FF")
                val rangeColor = Color("#0066FF")
                ReactHTML.div {
                    if (props.resolved) {
                        +"You thought that the most probable answer would be around "
                    } else {
                        +"You think that the most probable answer is around "
                    }
                    ReactHTML.b {
                        css { this.color = MainPalette.center.color }
                        +dist.space.formatValue(when (dist) {
                            is TruncatedNormalDistribution -> dist.pseudoMean
                            is TruncatedSplitNormalDistribution -> dist.center
                            else -> dist.median
                        })
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
        }
        ClientExtension.forEach { it.myPredictionDescriptionExtra(props, this) }
    }
}

