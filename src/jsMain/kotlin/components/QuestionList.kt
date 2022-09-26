package components

import emotion.react.css
import icons.EditIcon
import icons.ExpandMore
import mui.material.*
import mui.material.styles.TypographyVariant
import react.*
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML.em
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.strong
import react.router.useNavigate
import space.kscience.dataforge.values.Value
import space.kscience.dataforge.values.asValue
import tools.confido.distributions.*
import tools.confido.question.*
import utils.*
import kotlin.js.Date


external interface QuestionAnswerFormProps<T : AnswerSpace> : Props {
    var id: String
    var enabled: Boolean
    var answerSpace: T
    var prediction: Prediction?
}

fun postPrediction(prediction: Prediction, qid: String) {
    Client.postData("/send_prediction/${qid}", prediction)
}

val NumericQuestion = FC<QuestionAnswerFormProps<NumericAnswerSpace>> { props ->
    val answerSpace = props.answerSpace
    val prediction = props.prediction as? NumericPrediction ?: NumericPrediction((answerSpace.min + answerSpace.max) / 2.0, (answerSpace.max - answerSpace.min) / 4)
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

    fun formatDate(value: Number): String = Date(value.toDouble() * 1000.0).toISOString().substring(0, 10)
    fun formatUncertainty(value: Number): String = "Specify your uncertainty"

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
                valueLabelFormat = ::formatUncertainty
            track = if (madeUncertainty) "normal" else false.asDynamic()
            onFocus = { madePrediction = true; madeUncertainty = true }
            onChange = { _, value, _ -> stdDev = value }
            onChangeCommitted = { _, _ -> sendPrediction() }
        }
        if (madePrediction && madeUncertainty)
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
    var madePrediction by useState(props.prediction != null)

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
                        +"There is "
                        strong {
                            +"absolutely no chance"
                        }
                        +". (Are you sure?)"
                        certaintyExplanation()
                    }
                    100.0 -> {
                        +"This is an "
                        strong {
                            +"absolute certainty"
                        }
                        +". (Are you sure?)"
                        certaintyExplanation()
                    }
                    else -> {
                        +"There is "
                        strong {
                            +"$estimate %"
                        }
                        +" chance."
                    }
                }
            }
        }
    }
}

val QuestionList = FC<Props> {
    val appState = useContext(AppStateContext)
    val questions = appState.questions.values.sortedBy { it.name }
    val visibleQuestions = questions.filter { it.visible }

    val navigate = useNavigate()

    var editQuestion by useState<Question?>(null)
    var editOpen by useState(false)

    if (editOpen) {
        EditQuestionDialog {
            question = editQuestion
            open = editOpen
            onClose = { editOpen = false }
        }
    }
    Button {
        +"Create question"
        onClick = {editQuestion = null; editOpen = true}
    }

    visibleQuestions.map { question ->
        Accordion {
            TransitionProps = jsObject { unmountOnExit = true }
            key = question.id
            AccordionSummary {
                id = question.id
                expandIcon = ExpandMore.create()
                Typography {
                    variant = TypographyVariant.h4
                    +question.name
                }
                if (appState.userPredictions[question.id] == null && question.enabled) {
                    Chip {
                        label = ReactNode("Make a prediction")
                        variant = ChipVariant.outlined
                        size = Size.small
                    }
                }
                if (question.resolved) {
                    Chip {
                        label = ReactNode("Resolved")
                        variant = ChipVariant.outlined
                        size = Size.small
                    }
                }
                if (appState.isAdmin) {
                    IconButton {
                        onClick = {editQuestion = question; editOpen = true; it.stopPropagation()}
                        EditIcon {}
                    }
                }
            }
            AccordionDetails {
                when (val answerSpace = question.answerSpace) {
                    is NumericAnswerSpace ->
                        NumericQuestion {
                            this.id = question.id
                            this.enabled = question.enabled
                            this.answerSpace = answerSpace
                            this.prediction = appState.userPredictions[question.id]
                        }
                    is BinaryAnswerSpace ->
                        BinaryQuestion {
                            this.id = question.id
                            this.enabled = question.enabled
                            this.answerSpace = answerSpace
                            this.prediction = appState.userPredictions[question.id]
                        }
                }
                if (question.predictionsVisible) {
                    Typography {
                        +"Group predictions:"
                        Button {
                            onClick = { navigate("/group_predictions") }
                            +"Go"
                        }
                    }
                }
            }
        }
    }
}