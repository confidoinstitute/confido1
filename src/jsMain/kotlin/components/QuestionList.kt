package components

import csstype.ColorProperty
import emotion.react.css
import hooks.useElementSize
import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mui.material.*
import org.w3c.dom.HTMLSpanElement
import react.*
import react.dom.html.ReactHTML.span
import space.kscience.dataforge.values.asValue
import space.kscience.plotly.models.*
import tools.confido.distributions.*
import tools.confido.question.*
import utils.*


external interface QuestionAnswerFormProps<T : AnswerSpace> : Props {
    var id: String
    var answerSpace: T
}

fun postPrediction(prediction: Prediction, answerSpace: AnswerSpace) {
    window.alert(Json.encodeToString(prediction))
}

val NumericQuestion = FC<QuestionAnswerFormProps<NumericAnswerSpace>> { props ->
    val answerSpace = props.answerSpace

    var mean by useState(answerSpace.min)
    var stdDev by useState(0.0)

    val dist = TruncatedNormalDistribution(mean, stdDev, answerSpace.min, answerSpace.max)

    val confidences = listOf(
        ConfidenceColor(0.9, "#039be5".asValue()),
        ConfidenceColor(0.7, "#1565c0".asValue()),
        ConfidenceColor(0.5, "#311b92".asValue())
    )

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
            this.confidences = confidences
        }

        MarkedSlider {
            value = mean
            min = answerSpace.min
            max = answerSpace.max
            step = 0.1
            valueLabelDisplay = "auto"
            onChange = { _, value, _ -> mean = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
        MarkedSlider {
            value = stdDev
            min = 0
            max = (answerSpace.max - answerSpace.min) / 2
            step = 0.1
            valueLabelDisplay = "auto"
            onChange = { _, value, _ -> stdDev = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
        confidences.map { confidence ->
            Typography {
                val confidenceInterval = dist.confidenceInterval(1 - confidence.p)
                +"You are "
                span {
                    css {
                        color = csstype.Color(confidence.color.toString())
                    }
                    +"${confidence.p * 100}%"
                }
                +" confident that the value lies between ${confidenceInterval.first.format(1)} and ${confidenceInterval.second.format(1)}"
            }
        }
    }

}

val BinaryQuestion = FC<QuestionAnswerFormProps<BinaryAnswerSpace>> { props ->
    var estimate by useState(50)

    fun formatPercent(value: Number): String = "$value %"

    fun sendPrediction() {
        val pred = BinaryPrediction(estimate / 100.0)
        postPrediction(pred, props.answerSpace)
    }

    fun getMarks(width: Double) = when(width) {
        in 0.0 .. 500.0 -> emptyList()
        in 500.0 .. 960.0 -> listOf(0, 100)
        else -> listOf(0, 50, 100)
    }

    Fragment {
        MarkedSlider {
            defaultValue = 50
            min = 0
            max = 100
            this.widthToMarks = ::getMarks
            valueLabelDisplay = "auto"
            valueLabelFormat = ::formatPercent
            onChange = { _, value, _ -> estimate = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
    }
}

val QuestionList = FC<Props> {
    val appState = useContext(AppStateContext)
    val questions = appState.questions
    val visibleQuestions = questions.filter { it.visible }

    visibleQuestions.map { question ->
        Accordion {
            key = question.id
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