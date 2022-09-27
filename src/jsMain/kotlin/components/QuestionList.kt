package components

import csstype.Position
import csstype.px
import emotion.react.css
import hooks.useDebounce
import icons.AddIcon
import icons.EditIcon
import icons.ExpandMore
import kotlinx.js.timers.clearInterval
import kotlinx.js.timers.setInterval
import mui.material.*
import mui.material.styles.TypographyVariant
import react.*
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import tools.confido.question.*
import utils.*
import kotlin.math.floor

external interface QuestionItemProps : Props {
    var question: Question
    var prediction: Prediction?
    var editable: Boolean
    var onEditDialog: ((Question) -> Unit)?
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
val QuestionItem = FC<QuestionItemProps> { props ->
    val question = props.question
    val navigate = useNavigate()

    var pendingPrediction: Prediction? by useState(null)

    // Actual prediction was updated by app state update
    useEffect(props.prediction) {
        pendingPrediction = null
    }
    // Pending predictdion was updated by the input
    useDebounce(5000, pendingPrediction) {
        pendingPrediction?.let {
            postPrediction(it, props.question.id)
        }
    }

    var predictionAgoText by useState("")
    useEffect(props.prediction) {
        if (props.prediction == null)
            return@useEffect
        val timestamp = props.prediction!!.timestamp

        fun setText() {
            predictionAgoText = when(val difference = now() - timestamp) {
                in 0.0..10.0 -> "now"
                in 10.0..120.0 -> "${floor(difference)} s"
                in 120.0..7200.0 -> "${floor(difference / 60)} min"
                in 7200.0..172800.0 -> "${floor(difference / 3600)} h"
                else -> "${floor(difference / 86400)} days"
            }
        }
        setText()
        val interval = setInterval(::setText,5000)

        cleanup {
            clearInterval(interval)
        }
    }

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
            if (pendingPrediction != null) {
                Chip {
                    label = span.create {
                        CircularProgress {
                            this.size = "0.8rem"
                        }
                        +"Sending prediction..."
                    }
                    variant = ChipVariant.outlined
                }
            } else if (props.prediction == null && question.enabled) {
                Chip {
                    label = ReactNode("Make a prediction")
                    variant = ChipVariant.outlined
                }
            } else if (props.prediction != null) {
                Chip {
                    label = span.create {
                        +"Prediction accepted "
                        small {
                            +predictionAgoText
                        }
                    }
                    variant = ChipVariant.outlined
                }
            }
            if (question.resolved) {
                Chip {
                    label = ReactNode("Resolved")
                    variant = ChipVariant.outlined
                }
            }
            if (props.editable) {
                IconButton {
                    onClick = { props.onEditDialog?.invoke(question) }
                    EditIcon {}
                }
            }
        }
        AccordionDetails {
            val questionInput: FC<QuestionInputProps<AnswerSpace>> = when (question.answerSpace) {
                is BinaryAnswerSpace -> BinaryQuestionInput as FC<QuestionInputProps<AnswerSpace>>
                is NumericAnswerSpace -> NumericQuestionInput as FC<QuestionInputProps<AnswerSpace>>
            }
            questionInput {
                this.id = question.id
                this.enabled = question.enabled
                this.answerSpace = question.answerSpace
                this.prediction = pendingPrediction ?: props.prediction
                this.onPredict = {pendingPrediction = it}
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

val QuestionList = FC<Props> {
    val appState = useContext(AppStateContext)
    val questions = appState.questions.values.sortedBy { it.name }
    val visibleQuestions = questions.filter { it.visible }

    var editQuestion by useState<Question?>(null)
    var editOpen by useState(false)

    if (editOpen) {
        EditQuestionDialog {
            question = editQuestion
            open = editOpen
            onClose = { editOpen = false }
        }
    }

    visibleQuestions.map { question ->
        QuestionItem {
            this.question = question
            this.prediction = appState.userPredictions[question.id]
            this.editable = appState.isAdmin
            this.onEditDialog = { editQuestion = it; editOpen = true }
        }
    }

    if (appState.isAdmin) {
        Fab {
            css {
                position = Position.absolute
                right = 16.px
                bottom = 16.px
            }
            onClick = { editQuestion = null; editOpen = true }
            this.size = Size.small
            AddIcon {}
        }
    }
}