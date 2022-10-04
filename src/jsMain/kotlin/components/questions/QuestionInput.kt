package components.questions

import components.Explanation
import components.MarkedSlider
import csstype.Color
import emotion.react.css
import mui.material.Slider
import mui.material.Typography
import react.*
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.em
import space.kscience.dataforge.values.Value
import space.kscience.dataforge.values.asValue
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.question.*
import utils.dateMarkSpacing
import utils.format
import utils.now
import kotlin.js.Date

external interface QuestionInputProps<T : AnswerSpace> : Props {
    var id: String
    var enabled: Boolean
    var answerSpace: T
    var prediction: Prediction?
    var onPredict: ((Prediction) -> Unit)?
}

val NumericQuestionInput = FC<QuestionInputProps<NumericAnswerSpace>> { props ->
    val answerSpace = props.answerSpace
    console.log(answerSpace)
    val prediction = props.prediction as? NumericPrediction ?: NumericPrediction(0.0, (answerSpace.min + answerSpace.max) / 2.0, (answerSpace.max - answerSpace.min) / 4)
    var madePrediction by useState(props.prediction != null)
    var madeUncertainty by useState(madePrediction)

    var mean by useState(prediction.mean)
    var stdDev by useState(prediction.stdDev)

    val dist = TruncatedNormalDistribution(mean, stdDev, answerSpace.min, answerSpace.max)

    val step = if (props.answerSpace.representsDays) 86400 else 0.1

    val confidences = useMemo(props.enabled) {
        if (props.enabled) listOf(
            ConfidenceColor(0.9, "#039be5".asValue()),
            ConfidenceColor(0.7, "#1565c0".asValue()),
            ConfidenceColor(0.5, "#311b92".asValue())
        ) else listOf(
            ConfidenceColor(0.9, "#bfbfbf".asValue()),
            ConfidenceColor(0.7, "#acacac".asValue()),
            ConfidenceColor(0.5, "#a0a0a0".asValue())
        )
    }

    fun sendPrediction() {
        val pendingPrediction = NumericPrediction(now(), mean, stdDev)
        props.onPredict?.invoke(pendingPrediction)
    }

    fun formatDate(value: Number): String = Date(value.toDouble() * 1000.0).toISOString().substring(0, 10)
    val formatUncertainty = {_: Number -> "Specify your uncertainty"}

    fun addUnit(value: Any) = if (answerSpace.unit.isNotEmpty()) "$value ${answerSpace.unit}" else value.toString()

    Fragment {
        DistributionPlot {
            min = answerSpace.min
            max = answerSpace.max
            distribution = dist
            this.confidences = confidences
            outsideColor = if (props.enabled) Value.of("#000e47") else Value.of("#9c9c9c")
            this.visible = madePrediction && madeUncertainty
        }

        MarkedSlider {
            ariaLabel = "Mean Value"

            disabled = !props.enabled
            value = mean
            min = answerSpace.min
            max = answerSpace.max
            this.step = step
            this.madePrediction = madePrediction

            valueLabelDisplay = if (madePrediction || !props.enabled) "auto" else "on"
            if (answerSpace.representsDays) {
                this.valueLabelFormat = ::formatDate
                this.widthToMarks = { width -> dateMarkSpacing(width, answerSpace.min, answerSpace.max) }
            } else {
                this.valueLabelFormat = ::addUnit
            }

            onFocus = { madePrediction = true }
            onChange = { _, value, _ -> mean = value }
            onChangeCommitted = { _, _ -> if (madeUncertainty) sendPrediction() }
        }
        Slider {
            ariaLabel = "Uncertainty"

            disabled = !props.enabled || !madePrediction
            value = stdDev
            min = 0.1
            max = (answerSpace.max - answerSpace.min) / 2
            this.step = 0.1

            valueLabelDisplay = if (madeUncertainty || !madePrediction) "off" else "on"
            if (!madeUncertainty)
                valueLabelFormat = formatUncertainty
            track = if (madeUncertainty) "normal" else false.asDynamic()
            onFocus = { madePrediction = true; madeUncertainty = true }
            onChange = { _, value, _ -> stdDev = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
        if (madePrediction && madeUncertainty) {
            confidences.map { confidence ->
                Typography {
                    val confidenceInterval = dist.confidenceInterval(1 - confidence.p)
                    +"You are "
                    ReactHTML.strong {
                        css {
                            color = Color(confidence.color.toString())
                        }
                        +"${confidence.p * 100}%"
                    }
                    if (props.answerSpace.representsDays)
                        +" confident that the value lies between ${formatDate(confidenceInterval.first)} and ${
                            formatDate( confidenceInterval.second )
                        }"
                    else
                        +" confident that the value lies between ${addUnit(confidenceInterval.first.format(1))} and ${addUnit(confidenceInterval.second.format(1))}"
                }
            }
        }
    }
}

val BinaryQuestionInput = FC<QuestionInputProps<BinaryAnswerSpace>> { props ->
    val prediction = props.prediction as? BinaryPrediction ?: BinaryPrediction(0.0, 0.5)
    var estimate by useState(prediction.estimate * 100)
    var madePrediction by useState(props.prediction != null)

    fun formatPercent(value: Number): String = "$value %"


    fun sendPrediction() {
        val pendingPrediction = BinaryPrediction(now(), estimate / 100)
        props.onPredict?.invoke(pendingPrediction)
    }

    fun getMarks(width: Double) = when(width) {
        in 0.0 .. 500.0 -> emptyList()
        in 500.0 .. 960.0 -> listOf(0, 100)
        else -> listOf(0, 50, 100)
    }

    Fragment {
        MarkedSlider {
            ariaLabel = "Estimate"

            disabled = !props.enabled
            value = estimate
            min = 0
            max = 100

            this.widthToMarks = ::getMarks
            valueLabelDisplay = if (madePrediction || !props.enabled) "auto" else "on"
            valueLabelFormat = ::formatPercent
            this.madePrediction = madePrediction

            onFocus = { madePrediction = true }
            onChange = { _, value, _ -> estimate = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
        Typography {
            fun certaintyExplanation() {
                Explanation {
                    title = "Absolute certainty"
                    Typography {
                        +"To say something with absolute certainty is an extremely strong claim. In most cases during forecasting, there is still even a minimal chance that the true answer is different from your expectations."
                    }
                }
            }
            if (madePrediction) {
                when (estimate) {
                    0.0 -> {
                        +"You think there is "
                        ReactHTML.strong {
                            +"absolutely no chance"
                        }
                        +". (Are you sure?)"
                        certaintyExplanation()
                    }
                    100.0 -> {
                        +"You think this is an "
                        ReactHTML.strong {
                            +"absolute certainty"
                        }
                        +". (Are you sure?)"
                        certaintyExplanation()
                    }
                    else -> {
                        +"You think there is a "
                        ReactHTML.strong {
                            +"$estimate%"
                        }
                        +" chance."
                    }
                }
            } else {
                em {
                    +"No prediction made yet."
                }
            }
        }
    }
}
