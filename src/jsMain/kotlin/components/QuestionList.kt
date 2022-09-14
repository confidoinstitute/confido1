package components

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mui.material.*
import react.*
import space.kscience.dataforge.values.asValue
import space.kscience.plotly.layout
import space.kscience.plotly.models.*
import tools.confido.distributions.*
import tools.confido.question.*
import utils.*


external interface QuestionAnswerFormProps<T : AnswerSpace> : Props {
    var id: String
    var answerSpace: T
}

fun mark(value: Number, label: String?) = jsObject {
    this.value = value
    this.label = label
}

fun postPrediction(prediction: Prediction, answerSpace: AnswerSpace) {
    window.alert(Json.encodeToString(prediction))
}

val NumericQuestion = FC<QuestionAnswerFormProps<NumericAnswerSpace>> { props ->
    val answerSpace = props.answerSpace

    var mean by useState(answerSpace.min)
    var stdDev by useState(0.0)

    val dist = TruncatedNormalDistribution(mean, stdDev, answerSpace.min, answerSpace.max)

    fun sendPrediction() {
        val pred = NumericPrediction(mean, stdDev)
        postPrediction(pred, answerSpace)
    }

    Fragment {
        DistributionPlot {
            id = "${props.id}_plot"
            min = answerSpace.min
            max = answerSpace.max
            step = 0.5
            distribution = dist
            confidences = listOf(
                ConfidenceColor(0.9, "#333333".asValue()),
                ConfidenceColor(0.7, "#666666".asValue()),
                ConfidenceColor(0.5, "#999999".asValue())
            )
        }

        Slider {
            value = mean
            min = answerSpace.min
            max = answerSpace.max
            step = 0.1
            valueLabelDisplay = "auto"
            onChange = { _, value, _ -> mean = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
        Slider {
            value = stdDev
            min = 0
            max = (answerSpace.max - answerSpace.min) / 2
            step = 0.1
            valueLabelDisplay = "auto"
            onChange = { _, value, _ -> stdDev = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
        listOf(0.9, 0.7, 0.5).map { p ->
            Typography {
                val confidence = dist.confidenceInterval(1 - p)
                +"You are ${p * 100}% confident that the value lies between ${confidence.first.format(1)} and ${
                    confidence.second.format(
                        1
                    )
                }"
            }
        }
    }

}

val BinaryQuestion = FC<QuestionAnswerFormProps<BinaryAnswerSpace>> { props ->
    var estimate by useState(50)

    fun formatPercent(value: Int): String = "$value %"

    fun sendPrediction() {
        val pred = BinaryPrediction(estimate / 100.0)
        postPrediction(pred, props.answerSpace)
    }

    Fragment {
        Slider {
            defaultValue = 50
            min = 0
            max = 100
            valueLabelDisplay = "auto"
            valueLabelFormat = ::formatPercent
            marks = arrayOf(
                mark(0, "0 %"),
                mark(100, "100 %"),
            )
            onChange = { _, value, _ -> estimate = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
    }
}

external interface QuestionListProps : Props {
    var questions: List<Question>
}

val QuestionList = FC<QuestionListProps> { props ->
    val visibleQuestions = props.questions.filter { it.visible }

    visibleQuestions.map { question ->
        Accordion {
            AccordionSummary {
                id = question.id
                Typography {
                    +question.name
                }
            }
            AccordionDetails {
                when (val answerSpace = question.answerSpace) {
                    is NumericAnswerSpace ->
                        NumericQuestion {
                            this.id = question.id
                            this.answerSpace = answerSpace
                        }
                    is BinaryAnswerSpace ->
                        BinaryQuestion {
                            this.id = question.id
                            this.answerSpace = answerSpace
                        }
                }
            }
        }
    }
}