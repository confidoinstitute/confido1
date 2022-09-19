package components

import emotion.react.css
import mui.material.*
import react.*
import react.dom.html.ReactHTML.span
import space.kscience.dataforge.values.asValue
import tools.confido.distributions.*
import tools.confido.question.*
import utils.*
import kotlin.js.Date


external interface QuestionAnswerFormProps<T : AnswerSpace> : Props {
    var id: String
    var answerSpace: T
    var prediction: Prediction?
}

fun postPrediction(prediction: Prediction, qid: String) {
    Client.postData("/send_prediction/${qid}", prediction)
}

val NumericQuestion = FC<QuestionAnswerFormProps<NumericAnswerSpace>> { props ->
    val answerSpace = props.answerSpace
    val prediction = props.prediction as? NumericPrediction ?: NumericPrediction(answerSpace.min, 0.1)

    var mean by useState(prediction.mean)
    var stdDev by useState(prediction.stdDev)

    val dist = TruncatedNormalDistribution(mean, stdDev, answerSpace.min, answerSpace.max)

    val step = if (props.answerSpace.representsDays) 86400 else 0.1

    val confidences = listOf(
        ConfidenceColor(0.9, "#039be5".asValue()),
        ConfidenceColor(0.7, "#1565c0".asValue()),
        ConfidenceColor(0.5, "#311b92".asValue())
    )

    fun formatDate(value: Number): String = Date(value.toDouble() * 1000.0).toISOString().substring(0, 10)

    fun sendPrediction() {
        val pred = NumericPrediction(mean, stdDev)
        postPrediction(pred, props.id)
    }

    Fragment {
        DistributionPlot {
            id = "${props.id}_plot"
            min = answerSpace.min
            max = answerSpace.max
            this.bins = 200
            distribution = dist
            this.confidences = confidences
        }

        MarkedSlider {
            value = mean
            min = answerSpace.min
            max = answerSpace.max
            this.step = step
            valueLabelDisplay = "auto"
            if (answerSpace.representsDays) {
                this.valueLabelFormat = ::formatDate
                this.widthToMarks = { width -> dateMarkSpacing(width, answerSpace.min, answerSpace.max) }
            }
            onChange = { _, value, _ -> mean = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
        Slider {
            value = stdDev
            min = 0
            max = (answerSpace.max - answerSpace.min) / 2
            this.step = 0.1
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
                if (props.answerSpace.representsDays)
                    +" confident that the value lies between ${formatDate(confidenceInterval.first)} and ${formatDate(confidenceInterval.second)}"
                else
                    +" confident that the value lies between ${confidenceInterval.first.format(1)} and ${confidenceInterval.second.format(1)}"
            }
        }
    }

}

val BinaryQuestion = FC<QuestionAnswerFormProps<BinaryAnswerSpace>> { props ->
    val prediction = props.prediction as? BinaryPrediction ?: BinaryPrediction(0.5)
    var estimate by useState(prediction.estimate * 100)

    fun formatPercent(value: Number): String = "$value %"

    fun sendPrediction() {
        val pred = BinaryPrediction(estimate / 100.0)
        postPrediction(pred, props.id)
    }

    fun getMarks(width: Double) = when(width) {
        in 0.0 .. 500.0 -> emptyList()
        in 500.0 .. 960.0 -> listOf(0, 100)
        else -> listOf(0, 50, 100)
    }

    Fragment {
        MarkedSlider {
            value = estimate
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
    val questions = appState.questions.values.sortedBy { it.name }
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
                            this.prediction = appState.userPredictions[question.id]
                        }
                    is BinaryAnswerSpace ->
                        BinaryQuestion {
                            this.id = question.id
                            this.answerSpace = answerSpace
                            this.prediction = appState.userPredictions[question.id]
                        }
                }
            }
        }
    }
}