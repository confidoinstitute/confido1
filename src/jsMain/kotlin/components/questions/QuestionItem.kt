package components.questions

import Client
import components.*
import components.layout.permanentBreakpoint
import components.presenter.PresenterButton
import components.rooms.RoomContext
import csstype.*
import dom.html.HTMLDivElement
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.js.jso
import mui.material.transitions.TransitionProps
import mui.system.Breakpoint
import hooks.*
import icons.*
import kotlinx.js.jso
import mui.material.*
import mui.system.responsive
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.strong
import rooms.RoomPermission
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.spaces.*
import tools.confido.state.FeatureFlag
import tools.confido.state.InviteLinkPV
import tools.confido.state.QuestionPV
import tools.confido.state.enabled
import tools.confido.utils.unixNow
import utils.*
import web.timers.clearInterval
import web.timers.setInterval

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
    var commentCount: Int
    var onEditDialog: ((Question) -> Unit)?
    var onChange: ((Boolean) -> Unit)?
    var expanded: Boolean
}

var lastCommentSnackTS: Int = 0

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
val QuestionItem = FC<QuestionItemProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val question = props.question

    var pendingPrediction: ProbabilityDistribution? by useState(null)
    var pendingPredictionState by useState(PendingPredictionState.NONE)
    var snackOpen by useState(false)
    var commentsOpen by useState(false)

    useDebounce(5000, pendingPredictionState) {
        if (pendingPredictionState in listOf(PendingPredictionState.ACCEPTED, PendingPredictionState.ERROR))
            pendingPredictionState = PendingPredictionState.NONE
    }

    useDebounce(1000, pendingPrediction) {
        pendingPrediction?.let { dist ->
            runCoroutine {
                pendingPredictionState = PendingPredictionState.SENDING
                Client.sendData("/questions/${question.id}/predict", dist, onError = {
                    pendingPredictionState = PendingPredictionState.ERROR
                }) {
                    pendingPredictionState = PendingPredictionState.ACCEPTED
                    if (FeatureFlag.ENCOURAGE_COMMENTS.enabled && unixNow() - lastCommentSnackTS >= 24*3600) {
                        snackOpen = true
                        lastCommentSnackTS = unixNow()
                    }
                }
                pendingPrediction = null
            }
        }
    }
    useOnUnmount(pendingPrediction) { runCoroutine { Client.sendData("/questions/${question.id}/predict", it, onError = {showError?.invoke(it)}) {} } }

    Snackbar {
        open = snackOpen
        anchorOrigin = jso { vertical=SnackbarOriginVertical.bottom; horizontal=SnackbarOriginHorizontal.center}
        message = ReactNode("Thank you for your prediction. You can use comments to explain the thinking behind your prediction to others.")
        autoHideDuration = 5000
        action = Fragment.create {
            Button {
                color = ButtonColor.secondary
                onClick = { snackOpen = false; commentsOpen = true }
                +"Write a comment"
            }
            IconButton {
                CloseIcon{}
                size = mui.material.Size.small
                color = IconButtonColor.inherit
                onClick = { snackOpen = false }
            }
        }
        onClose = { _, _ -> snackOpen = false }
    }

    Accordion {
        className = ClassName("questionitem")
        id = "questionitem-${props.question.id}"
        expanded = props.expanded
        onChange = { _, state -> props.onChange?.invoke(state) }
        TransitionProps = buildObject { unmountOnExit = true }
        val sz = useElementSize<HTMLDivElement>()
        val chipsSz = useElementSize<HTMLDivElement>()
        val chipsUnder = (sz.width != 0.0 && chipsSz.width != 0.0 && sz.width - chipsSz.width < 600)
        AccordionSummary {
            ref = sz.ref
            id = question.id
            expandIcon = ExpandMore.create()
            Stack {
                this.direction = responsive(if (chipsUnder) StackDirection.column else StackDirection.row)
                this.spacing = responsive(1)
                sx {
                    alignItems = if (chipsUnder) AlignItems.flexStart else AlignItems.center
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
                Stack {
                    ref = chipsSz.ref
                    this.direction = responsive(StackDirection.row)
                    this.spacing = responsive(1)
                    sx {
                        alignItems = AlignItems.center
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
                            distribution = null
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
            PresenterButton {
                view = QuestionPV(question.ref)
            }
            // TODO turn it into a component
            UpdatesButton {
                this.question = props.question
            }
            GroupPredButton {
                this.question = question
                this.disabled =
                    !(question.groupPredVisible || appState.hasPermission( room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS ))
                this.count = props.question.numPredictors
            }
            QuestionCommentsDialog {
                this.open = commentsOpen
                this.question = props.question
                this.numComments = props.commentCount
                this.prediction = props.prediction
                this.onClose = { commentsOpen = false }
            }
            QuestionCommentsButton {
                this.numComments = props.commentCount
                this.onClick = { commentsOpen = true }
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
