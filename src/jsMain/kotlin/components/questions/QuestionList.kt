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
import react.dom.html.ReactHTML.strong
import react.router.*
import tools.confido.distributions.*
import tools.confido.question.*
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.*
import kotlin.coroutines.EmptyCoroutineContext

enum class PendingPredictionState {
    NONE,
    MAKING,
    SENDING,
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
        val timestamp = props.prediction!!.ts

        fun setText() {
            predictionAgoText = durationAgo(unixNow() - timestamp)
        }
        setText()
        val interval = setInterval(::setText,5000)

        cleanup {
            clearInterval(interval)
        }
    }

    when(props.state) {
        PendingPredictionState.MAKING -> Chip {
            label = ReactNode("Predicting...")
            variant = ChipVariant.outlined
        }
        PendingPredictionState.SENDING -> Chip {
            label = span.create {
                CircularProgress {
                    this.size = "0.8rem"
                }
                +"Sending prediction..."
            }
            variant = ChipVariant.outlined
        }
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
            if (props.prediction == null && props.enabled) {
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
                            this.title = ReactNode(props.prediction!!.ts.toDateTime())
                            this.placement = TooltipPlacement.left
                            small {
                                +predictionAgoText
                            }
                        }
                    }
                    variant = ChipVariant.outlined
                }
            } else {
                Chip {
                    label = ReactNode("Predictions closed")
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
    var onChange: ((Boolean) -> Unit)?
    var expanded: Boolean
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
val QuestionItem = FC<QuestionItemProps> { props ->
    val stale = useContext(AppStateContext).stale
    val question = props.question
    val navigate = useNavigate()

    var pendingPrediction: ProbabilityDistribution? by useState(null)
    var pendingPredictionState by useState(PendingPredictionState.NONE)

    useDebounce(5000, pendingPredictionState) {
        if (pendingPredictionState in listOf(PendingPredictionState.ACCEPTED, PendingPredictionState.ERROR))
            pendingPredictionState = PendingPredictionState.NONE
    }

    useDebounce(1000, pendingPrediction) {
        pendingPrediction?.let { dist ->
            CoroutineScope(EmptyCoroutineContext).launch {
                pendingPredictionState = PendingPredictionState.SENDING
                try {
                    Client.httpClient.postJson("/send_prediction/${props.question.id}", dist) {
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
        // TODO: Fix when clicking another question while one is already expanded.
        onChange = {_, state -> props.onChange?.invoke(state) }
        TransitionProps = jsObject { unmountOnExit = true }
        AccordionSummary {
            id = question.id
            expandIcon = ExpandMore.create()
            Stack {
                this.direction = responsive(StackDirection.row)
                this.spacing = responsive(1)
                sx {
                    alignItems = AlignItems.center
                    flexGrow = number(1.0)
                }
                Typography {
                    variant = TypographyVariant.h6
                        +question.name
                    sx {
                        flexGrow = number(1.0)
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
            val questionInput: FC<QuestionInputProps<Space, ProbabilityDistribution>> = when (question.answerSpace) {
                is BinarySpace -> BinaryQuestionInput as FC<QuestionInputProps<Space, ProbabilityDistribution>>
                is NumericSpace -> NumericQuestionInput as FC<QuestionInputProps<Space, ProbabilityDistribution>>
            }
            questionInput {
                this.id = question.id
                this.enabled = question.enabled && !stale
                this.space = question.answerSpace
                this.prediction = pendingPrediction ?: props.prediction?.dist
                this.onChange = { pendingPrediction = null; pendingPredictionState = PendingPredictionState.MAKING }
                this.onPredict = { pendingPrediction = it }
            }
            if (question.predictionsVisible) {
                Typography {
                    sx {
                        margin = Margin(1.asDynamic(), 0.px)
                        lineHeight = number(2.0)
                    }
                    strong {
                        +"Group predictions: "
                    }
                    DistributionSummary {
                        spoiler = true
                        allowPlotDialog = true
                        distribution = props.prediction?.dist
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

external interface QuestionListProps : Props {
    var questions: List<Question>
}

val QuestionList = FC<QuestionListProps> { props ->
    val clientAppState = useContext(AppStateContext)
    val appState = clientAppState.state
    val questions = props.questions.sortedBy { it.name }
    val visibleQuestions = if (appState.isAdmin) questions else questions.filter { it.visible }

    var editQuestion by useState<Question?>(null)
    var editQuestionKey by useState("")
    var editOpen by useState(false)
    useLayoutEffect(editOpen) {
        if (editOpen)
            editQuestionKey = randomString(20)
    }

    var expandedQuestion by useState<String?>(null)

    EditQuestionDialog {
        key = "##editDialog##$editQuestionKey"
        question = editQuestion
        open = editOpen
        onClose = { editOpen = false }
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
            this.onChange = {state -> expandedQuestion = if (state) question.id else null}
        }
    }

    if (appState.isAdmin && !clientAppState.stale) {
        Fragment {
            Button {
                this.key = "##add##"
                this.startIcon = AddIcon.create()
                this.color = ButtonColor.primary
                onClick = { editQuestion = null; editOpen = true }
                +"Add question…"
            }
        }
    }
}
