package components.questions

import components.*
import components.rooms.RoomContext
import csstype.*
import hooks.useDebounce
import hooks.useOnUnmount
import icons.EditIcon
import icons.ExpandMore
import icons.TimelineIcon
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.js.timers.clearInterval
import kotlinx.js.timers.setInterval
import mui.material.*
import mui.system.responsive
import mui.system.sx
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.strong
import rooms.RoomPermission
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Comment
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.Space
import tools.confido.utils.unixNow
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
    val (_, stale) = useContext(AppStateContext)
    var predictionAgoText by useState("")
    useEffect(props.prediction) {
        if (props.prediction == null)
            return@useEffect
        val timestamp = props.prediction!!.ts

        fun setText() {
            predictionAgoText = durationAgo(unixNow() - timestamp)
        }
        setText()
        val interval = setInterval(::setText, 5000)

        cleanup {
            clearInterval(interval)
        }
    }

    when (props.state) {
        PendingPredictionState.MAKING -> Chip {
            label = ReactNode("Predicting...")
            variant = ChipVariant.outlined
        }
        PendingPredictionState.SENDING -> Chip {
            label = ReactHTML.span.create {
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
                    label = ReactHTML.span.create {
                        +"Your last prediction: "
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
            } else if (!stale) {
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
    var canPredict: Boolean
    var editable: Boolean
    var comments: Map<String, Comment>
    var onEditDialog: ((Question) -> Unit)?
    var onChange: ((Boolean) -> Unit)?
    var expanded: Boolean
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
val QuestionItem = FC<QuestionItemProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val question = props.question

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
                    Client.httpClient.postJson("/questions/${props.question.id}/predict", dist) {
                        expectSuccess = true
                    }
                    pendingPredictionState = PendingPredictionState.ACCEPTED
                } catch (e: Throwable) {
                    pendingPredictionState = PendingPredictionState.ERROR
                } finally {
                    pendingPrediction = null
                }
            }
        }
    }
    useOnUnmount(pendingPrediction) { Client.postData("/questions/${props.question.id}/predict", it) }

    Accordion {
        expanded = props.expanded
        onChange = { _, state -> props.onChange?.invoke(state) }
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
                    +question.name
                    sx {
                        fontSize = 1.25.rem
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
                if (props.canPredict)
                    QuestionPredictionChip {
                        this.enabled = question.open && !stale
                        this.pending = pendingPrediction != null
                        this.prediction = props.prediction
                        this.state = pendingPredictionState
                    }
            }
        }
        AccordionDetails {
            Typography {
                sx {
                    whiteSpace = WhiteSpace.preLine
                    marginBottom = themed(2)
                }
                TextWithLinks {
                    text = question.description
                }
            }
            if (props.canPredict) {
                val questionInput: FC<QuestionInputProps<Space, ProbabilityDistribution>> =
                    when (question.answerSpace) {
                        is BinarySpace -> BinaryQuestionInput as FC<QuestionInputProps<Space, ProbabilityDistribution>>
                        is NumericSpace -> NumericQuestionInput as FC<QuestionInputProps<Space, ProbabilityDistribution>>
                    }
                questionInput {
                    this.id = question.id
                    this.enabled =
                        question.open && appState.hasPermission(room, RoomPermission.SUBMIT_PREDICTION) && !stale
                    this.space = question.answerSpace
                    this.prediction = pendingPrediction ?: props.prediction?.dist
                    this.onChange = { pendingPrediction = null; pendingPredictionState = PendingPredictionState.MAKING }
                    this.onPredict = { pendingPrediction = it }
                }
            }
            if (question.groupPredVisible || appState.hasPermission(
                    room,
                    RoomPermission.VIEW_ALL_GROUP_PREDICTIONS
                )
            ) {
                // Disable this as we now have button for group predictions
                if (false)
                Typography {
                    sx {
                        margin = Margin(themed(1), themed(0))
                        lineHeight = number(2.0)
                    }
                    strong {
                        +"Group prediction: "
                    }
                    SpoilerButton {
                        DistributionSummary {
                            allowPlotDialog = true
                            distribution = appState.groupPred[question.ref]?.dist
                        }
                    }
                }
            }
            question.resolution?.let { resolution->
                if (question.resolutionVisible || appState.hasPermission(
                        room,
                        RoomPermission.VIEW_ALL_RESOLUTIONS
                    )
                ) {
                    Typography {
                        sx {
                            margin = Margin(themed(1), themed(0))
                            lineHeight = number(2.0)
                        }
                        strong {
                            +"Resolution: "
                        }
                        SpoilerButton {
                            +(resolution.format())
                        }
                    }
                }
            }
        }
        AccordionActions {
            // TODO turn it into a component
            UpdatesButton {
                this.question = props.question
            }
            GroupPredButton {
                this.distribution = appState.groupPred[question.ref]?.dist
                this.disabled =
                    !(question.groupPredVisible || appState.hasPermission( room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS ))
                this.count = props.question.numPredictors
            }
            QuestionComments {
                this.question = props.question
                this.comments = props.comments
                this.prediction = props.prediction
            }
            if (props.editable) {
                // XXX: MUI CSS expects all children of AccordionIcons to be of same type
                // (usually IconButton, but we need span for tooltips on disabled buttons
                // above; so we use span also here)
                span {
                    IconButton {
                        disabled = stale
                        onClick = { props.onEditDialog?.invoke(question); it.stopPropagation() }
                        EditIcon {}
                    }
                }
            }
        }
    }
}
