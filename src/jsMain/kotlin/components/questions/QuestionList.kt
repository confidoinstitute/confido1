package components.questions

import components.*
import csstype.*
import emotion.react.css
import hooks.useDebounce
import hooks.useOnUnmount
import icons.AddIcon
import icons.EditIcon
import icons.ExpandMore
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.js.timers.clearInterval
import kotlinx.js.timers.setInterval
import mui.material.*
import mui.material.Size
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import react.router.useParams
import tools.confido.question.*
import utils.*
import kotlin.coroutines.EmptyCoroutineContext

enum class PendingPredictionState {
    NONE,
    ACCEPTED,
    ERROR,
}

external interface QuestionPredictionChipProps : Props {
    var pending: Boolean
    var enabled: Boolean
    var prediction: Prediction?
    var state: PendingPredictionState
}

val QuestionPredictionChip = FC<QuestionPredictionChipProps> { props ->
    var predictionAgoText by useState("")
    useEffect(props.prediction) {
        if (props.prediction == null)
            return@useEffect
        val timestamp = props.prediction!!.timestamp

        fun setText() {
            predictionAgoText = durationAgo(now() - timestamp)
        }
        setText()
        val interval = setInterval(::setText,5000)

        cleanup {
            clearInterval(interval)
        }
    }

    when(props.state) {
        PendingPredictionState.ACCEPTED -> Chip {
            label = ReactNode("Prediction submitted!")
            variant = ChipVariant.outlined
            color = ChipColor.success
        }
        PendingPredictionState.ERROR -> Chip {
            label = ReactNode("Prediction submission failed!")
            variant = ChipVariant.outlined
            color = ChipColor.error
        }
        PendingPredictionState.NONE ->
            if (props.pending) {
                Chip {
                    label = span.create {
                        CircularProgress {
                            this.size = "0.8rem"
                        }
                        +"Sending prediction..."
                    }
                    variant = ChipVariant.outlined
                }
            } else if (props.prediction == null && props.enabled) {
                Chip {
                    label = ReactNode("Not yet predicted")
                    variant = ChipVariant.outlined
                    color = ChipColor.primary
                }
            } else if (props.prediction != null) {
                Chip {
                    label = span.create {
                        +"Last prediction: "
                        Tooltip {
                            this.title = ReactNode(props.prediction!!.timestamp.toDateTime())
                            this.placement = TooltipPlacement.left
                            small {
                                +predictionAgoText
                            }
                        }
                    }
                    variant = ChipVariant.outlined
                }
            }
    }
}
external interface QuestionItemProps : Props {
    var question: Question
    var prediction: Prediction?
    var editable: Boolean
    var comments: List<Comment>
    var onEditDialog: ((Question) -> Unit)?
    var expanded: Boolean
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
val QuestionItem = FC<QuestionItemProps> { props ->
    val stale = useContext(AppStateContext).stale
    val question = props.question
    val navigate = useNavigate()

    var pendingPrediction: Prediction? by useState(null)
    var pendingPredictionState by useState(PendingPredictionState.NONE)

    useDebounce(5000, pendingPredictionState) {
        if (pendingPredictionState != PendingPredictionState.NONE)
            pendingPredictionState = PendingPredictionState.NONE
    }

    useDebounce(5000, pendingPrediction) {
        pendingPrediction?.let {
            CoroutineScope(EmptyCoroutineContext).launch {
                try {
                    Client.httpClient.postJson("/send_prediction/${props.question.id}", it) {
                        expectSuccess = true
                    }
                    pendingPredictionState = PendingPredictionState.ACCEPTED
                } catch(e: Throwable) {
                    pendingPredictionState = PendingPredictionState.ERROR
                } finally {
                    pendingPrediction = null
                }
            }
        }
    }
    useOnUnmount(pendingPrediction) {Client.postData("/send_prediction/${props.question.id}", it) }

    Accordion {
        expanded = props.expanded
        onChange = {_, state -> if(state) {navigate("/questions/${question.id}")} else {navigate("/")} }
        TransitionProps = jsObject { unmountOnExit = true }
        AccordionSummary {
            id = question.id
            expandIcon = ExpandMore.create()
            Stack {
                this.direction = responsive(StackDirection.row)
                this.spacing = responsive(1)
                sx {
                    alignItems = AlignItems.center
                    flexGrow = 1.asDynamic()
                }
                Typography {
                    variant = TypographyVariant.h4
                        +question.name
                    sx {
                        flexGrow = 1.asDynamic()
                        if (!question.visible) {
                            this.fontStyle = FontStyle.italic
                        }
                    }
                }
                if (question.resolved) {
                    Chip {
                        label = ReactNode("Resolved")
                        variant = ChipVariant.outlined
                    }
                }
                if (!question.visible) {
                    Chip {
                        label = ReactNode("Not visible")
                        variant = ChipVariant.outlined
                        color = ChipColor.warning
                    }
                }
                QuestionPredictionChip {
                    this.enabled = question.enabled && !stale
                    this.pending = pendingPrediction != null
                    this.prediction = props.prediction
                    this.state = pendingPredictionState
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
                this.enabled = question.enabled && !stale
                this.answerSpace = question.answerSpace
                this.prediction = pendingPrediction ?: props.prediction
                this.onPredict = {pendingPrediction = it; pendingPredictionState = PendingPredictionState.NONE }
            }
            if (question.predictionsVisible) {
                Typography {
                    +"Group predictions:"
                    Button {
                        onClick = { navigate("/group_predictions") }
                        +"Go (TODO)"
                    }
                }
            }
        }
        AccordionActions {
            QuestionComments {
                this.question = props.question
                this.comments = props.comments
                this.prediction = props.prediction
            }
            if (props.editable) {
                IconButton {
                    onClick = { props.onEditDialog?.invoke(question); it.stopPropagation() }
                    EditIcon {}
                }
            }
        }
    }
}

val QuestionList = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val appState = clientAppState.state
    val questions = appState.questions.values.sortedBy { it.name }
    val visibleQuestions = if (appState.isAdmin) questions else questions.filter { it.visible }

    var editQuestion by useState<Question?>(null)
    var editOpen by useState(false)

    val expandedQuestion = useParams().get("questionID")

    if (editOpen) {
        EditQuestionDialog {
            question = editQuestion
            open = editOpen
            onClose = { editOpen = false }
        }
    }

    fun editQuestionOpen(it: Question) {
        editQuestion = it; editOpen = true
    }
    visibleQuestions.map { question ->
        QuestionItem {
            this.key = question.id
            this.question = question
            this.expanded = question.id == expandedQuestion
            this.prediction = appState.userPredictions[question.id]
            this.editable = appState.isAdmin && !clientAppState.stale
            this.comments = appState.comments[question.id] ?: listOf()
            this.onEditDialog = ::editQuestionOpen
        }
    }

    if (appState.isAdmin && !clientAppState.stale) {
        Fab {
            css {
                position = Position.fixed
                right = 16.px
                bottom = 16.px
            }
            onClick = { editQuestion = null; editOpen = true }
            this.size = Size.small
            AddIcon {}
        }
    }
}