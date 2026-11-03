package tools.confido.web.components.questions.predictions

import tools.confido.question.BinaryHistogram
import tools.confido.web.components.basic.MainPalette
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.basic.sansSerif
import tools.confido.web.components.questions.dialog.ExactEstimateDialog
import web.cssom.Color
import web.cssom.Padding
import web.cssom.integer
import web.cssom.px
import emotion.react.css
import web.cssom.ClassName
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
    /** When a comparison is open, describe it instead of the current prediction
     *  (which the chart then isn't showing). */
    var compare: CompareSides?
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

val descriptionClass = emotion.css.ClassName(ClassName("prediction-desription")) {
    padding = Padding(25.px, 15.px)
    gap = 10.px
    color = Color("#222222")
    fontWeight = integer(500)
    fontFamily = sansSerif
    fontSize = 15.px
    lineHeight = 18.px
}

/** "an" for numbers read with a leading vowel sound (8…, 11, 18), else "a". */
private fun articleFor(number: String) =
    if (number.startsWith("8") || Regex("^1[18](?![0-9])").containsMatchIn(number)) "an" else "a"

private fun ChildrenBuilder.splitChip(text: String, color: Color, dark: Boolean = false) = ReactHTML.b {
    css {
        backgroundColor = color
        this.color = if (dark) Color("#FFFFFF") else Color("#333333")
        borderRadius = 3.px
        padding = Padding(0.px, 4.px)
    }
    +text
}

/**
 * The hovered graph's below/above split as a sentence, color-keyed like the
 * graph's own readout chips; stands in for the 80% CI sentence while hovering.
 */
private fun ChildrenBuilder.splitSentence(split: GraphSplit, present: String, past: String, resolved: Boolean) = div {
    fun chance(p: Double, color: Color) {
        val pct = formatPercent(p, space = false)
        +"${articleFor(pct)} "
        splitChip(pct, color)
        +" chance"
    }
    +"${if (resolved) past else present} "
    chance(split.below, splitBelowColor)
    +" that the answer ${if (resolved) "would be" else "is"} below "
    splitChip(split.label, Color("#555555"), dark = true)
    +", and "
    chance(1 - split.below, splitAboveColor)
    +" that it ${if (resolved) "would be" else "is"} above."
}

/**
 * An open comparison in sentences: how the median and the 80% range moved, or,
 * while the chart is hovered, how P(answer < cursor) moved. Older values in the
 * older side's color, newer in the newer's, matching the chart's legend.
 */
private fun ChildrenBuilder.compareSentences(c: CompareSides, split: GraphSplit?) {
    val older = c.distA
    val newer = c.distB
    // Extensions, not plain local funs: a local fun's implicit receiver is lexical
    // (the outer builder), so its output would escape the sentence's div.
    fun ChildrenBuilder.olderValue(text: String) = b { css { this.color = compareOldText }; +text }
    fun ChildrenBuilder.newerValue(text: String) = b { css { this.color = compareNewText }; +text }
    fun ChildrenBuilder.between() {
        +"Between "
        compareSideChip(0, c.labelA)
        +" and "
        compareSideChip(1, c.labelB)
        +", "
    }
    /** "moved from X to Y", or "stayed at X" when they print the same. */
    fun ChildrenBuilder.change(from: String, to: String) {
        if (from == to) { +"stayed at "; newerValue(to) }
        else { +"moved from "; olderValue(from); +" to "; newerValue(to) }
    }
    when {
        older is ContinuousProbabilityDistribution && newer is ContinuousProbabilityDistribution -> {
            val space = newer.space
            div {
                between()
                +"the median estimate "
                change(space.formatValue(older.median), space.formatValue(newer.median))
                +"."
            }
            if (split?.belowBefore != null) div {
                +"The chance that the answer is below "
                splitChip(split.label, Color("#555555"), dark = true)
                +" "
                change(formatPercent(split.belowBefore, space = false), formatPercent(split.below, space = false))
                +"."
            } else div {
                // The unit once per range ("24.6–55.4 kg"); dates carry none.
                fun range(d: ContinuousProbabilityDistribution) = d.confidenceInterval(0.8, preferredCenter = d.median)
                    .let { space.formatValue(it.start, showUnit = false) + "–" + space.formatValue(it.endInclusive) }
                +"The 80% range "
                change(range(older), range(newer))
                +"."
            }
        }
        older is BinaryDistribution && newer is BinaryDistribution -> div {
            between()
            +"the chance of "
            yesNoColored(true)
            +" "
            change(formatPercent(older.yesProb, space = false), formatPercent(newer.yesProb, space = false))
            val d = newer.yesProb - older.yesProb
            if (formatPercent(older.yesProb) != formatPercent(newer.yesProb))
                +", ${if (d > 0) "up" else "down"} ${formatPercent(kotlin.math.abs(d)).removeSuffix("%")} percentage points"
            +"."
        }
        // A side still loading or unavailable: the chart says so itself.
    }
}

val GroupPredictionDescription = FC<GroupPredictionDescriptionProps> { props ->
    val predTerm = props.predictionTerminology
    val split = useGraphSplit()
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
        props.compare?.let { compareSentences(it, split) }
        if (props.compare == null)
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
                if (split != null) splitSentence(split, "The group thinks there is", "The group thought there was", props.resolved)
                else div {
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
    val split = useGraphSplit()
    if (props.question != null)
    ExactEstimateDialog {
        question = props.question!!
        open = exactOpen
        onClose = {exactOpen = false}
    }
    val hideDefault = ClientExtension.enabled.any { it.hideDefaultPredictionDesc(props.dist, props.question, false) }
    Stack {
        className = ClassName("prediction-description")
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
                if (split != null) splitSentence(split, "You think there is", "You thought there was", props.resolved)
                else ReactHTML.div {
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

