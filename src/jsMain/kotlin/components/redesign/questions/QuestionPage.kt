package components.redesign.questions

import BinaryHistogram
import Client
import browser.window
import components.*
import components.questions.PendingPredictionState
import components.redesign.*
import components.redesign.TextWithLinks
import components.redesign.basic.*
import components.redesign.comments.*
import components.redesign.comments.Comment
import components.redesign.comments.CommentInputVariant
import components.redesign.forms.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.dialog.*
import components.redesign.questions.dialog.EditQuestionDialog
import components.redesign.questions.predictions.*
import components.redesign.rooms.*
import components.redesign.rooms.dialog.CsvExportDialog
import components.rooms.*
import csstype.*
import emotion.react.*
import hooks.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.js.*
import kotlinx.serialization.*
import kotlinx.serialization.json.encodeToDynamic
import payloads.responses.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.router.dom.Link
import react.router.useLocation
import react.router.useNavigate
import rooms.*
import tools.confido.distributions.*
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.serialization.*
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.*

external interface QuestionLayoutProps : Props {
    var question: Question
    var openResolve: Boolean?
}

external interface QuestionHeaderProps : Props {
    var text: String
    var description: String
    var resolution: Value?
    var questionState: QuestionState
    var isHidden: Boolean
    var stateHistory: List<QuestionStateChange>
}

external interface QuestionStatusProps : Props {
    var text: String
    var time: Instant?
}

external interface QuestionEstimateSectionProps : Props {
    var question: Question
    var resolution: Value?
    var myPrediction: Prediction?
    var groupPrediction: Prediction?
    var numPredictors: Int
}

external interface QuestionEstimateTabButtonProps : ButtonBaseProps {
    var active: Boolean
}

external interface QuestionCommentSectionProps : Props {
    var question: Question
    var myPrediction: Prediction?
    var allowAddingComment: Boolean
}

external interface BinaryHistogramSectionProps : Props {
    var question: Question
    var onHide: (() -> Unit)?
}


private val bgColor = Color("#f2f2f2")

val QuestionPage = FC<QuestionLayoutProps>("QuestionPage") { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val myPrediction = appState.myPredictions[props.question.ref]
    val groupPrediction = useWebSocket<Prediction?>("/state${props.question.urlPrefix}/group_pred")
    val layoutMode = useContext(LayoutModeContext)
    val roomPalette = room.color.palette

    var quickSettingsOpen by useState(false)
    var resolutionDialogOpen by useState(props.openResolve ?: false)
    var csvDialogOpen by useState(false)
    val loc = useLocation()
    val navigate = useNavigate()

    useEffect(props.question.id) { // TODO does not reflect change in question value
        window.asDynamic().curQuestion = confidoJSON.encodeToDynamic(props.question)
        cleanup {
            window.asDynamic().curQuestion = undefined
        }
    }

    useEffect(props.question.id) { // TODO does not reflect change in question value
        window.asDynamic().curQuestion = confidoJSON.encodeToDynamic(props.question)
        cleanup {
            window.asDynamic().curQuestion = undefined
        }
    }

    val editDialog = useEditDialog(EditQuestionDialog, jso {
        preset = QuestionPreset.NONE
    })

    Header {
        title = props.question.name
        appBarColor = roomPalette.color
    }

    if (!room.hasPermission(appState.session.user, RoomPermission.VIEW_QUESTIONS))
    // TODO proper no permission page
        return@FC

    QuestionQuickSettingsDialog {
        question = props.question
        open = quickSettingsOpen
        canEdit = room.hasPermission(appState.session.user, RoomPermission.MANAGE_QUESTIONS)
        onEdit = { quickSettingsOpen = false; editDialog(props.question) }
        onOpenResolution = { quickSettingsOpen = false; resolutionDialogOpen = true; }
        onExport = { quickSettingsOpen = false; csvDialogOpen = true; }
        onClose = { quickSettingsOpen = false }
        key="QuickSettingsDialog"
    }

    QuestionResolveDialog {
        open = resolutionDialogOpen
        question = props.question
        onClose = {
            resolutionDialogOpen = false
            if (loc.pathname.endsWith("/resolve")) navigate("..")
        }
        key = "ResolveDialog"
    }

    CsvExportDialog {
        key = "CsvExportDialog"
        question = props.question
        open = csvDialogOpen
        onClose = { csvDialogOpen = false }
    }

    RoomNavbar {
        palette = roomPalette
        navigateBack = room.urlPrefix
        onMenu = { quickSettingsOpen = true }
        Link {
            className = LinkUnstyled
            to = room.urlPrefix
            +room.name
        }
    }
    Stack {
        component = main
        css {
            marginTop = 44.px
            flexDirection = FlexDirection.column
            flexGrow = number(1.0)
            width = layoutMode.contentWidth
            marginLeft = Auto.auto
            marginRight = Auto.auto
        }

        QuestionHeader {
            this.text = props.question.name
            this.questionState = props.question.state
            this.stateHistory = props.question.stateHistory
            this.description = props.question.description
            if (props.question.resolutionVisible) {
                this.resolution = props.question.resolution
            }
            this.isHidden = !props.question.visible
        }
        QuestionPredictionSection {
            this.question = props.question
            if (props.question.state == QuestionState.RESOLVED) {
                this.resolution = props.question.resolution
            }
            this.myPrediction = myPrediction
            this.numPredictors = props.question.numPredictors
            this.groupPrediction = groupPrediction.data
        }
        if (props.question.allowComments && appState.hasPermission(room, RoomPermission.VIEW_QUESTION_COMMENTS)) {
            QuestionCommentSection {
                this.question = props.question
                this.myPrediction = myPrediction
                this.allowAddingComment = appState.hasPermission(room, RoomPermission.POST_QUESTION_COMMENT) && props.question.allowComments
            }
        }
    }
}

val QuestionEstimateTabButton = ButtonBase.withStyle<QuestionEstimateTabButtonProps>("active") { props ->
    all = Globals.unset
    cursor = Cursor.pointer

    borderRadius = 10.px
    padding = Padding(12.px, 10.px)

    flexGrow = number(1.0)
    textAlign = TextAlign.center

    fontFamily = sansSerif
    fontSize = 17.px
    lineHeight = 21.px

    ".ripple" {
        backgroundColor = Color("#DDDDDD")
    }

    if (props.active) {
        backgroundColor = Color("#FFFFFF")
        color = Color("#000000")
        fontWeight = integer(500)
    } else {
        hover {
            backgroundColor = Color("#DDDDDD")
        }

        color = Color("rgba(0, 0, 0, 0.5)")
        fontWeight = integer(400)
    }
}

external interface PredictionOverlayProps: PropsWithChildren {
    var dimBackground: Boolean?
}
val PredictionOverlay = FC<PredictionOverlayProps> { props ->
    div {
        css {
            zIndex = integer(10)
            position = Position.absolute
            top = 0.px
            left = 0.px
            width = 100.pct
            height = 100.pct
            if (props.dimBackground ?: true)
            backgroundColor = rgba(255, 255, 255, 0.75)
            fontFamily = sansSerif
            padding = Padding(52.px, 44.px)
            textAlign = TextAlign.center
            color = Color("#555555")
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            pointerEvents = None.none
        }
        +props.children
    }
}

private val QuestionPredictionSection = FC<QuestionEstimateSectionProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val layoutMode = useContext(LayoutModeContext)
    val room = useContext(RoomContext)
    val hasPredictPermission = appState.hasPermission(room, RoomPermission.SUBMIT_PREDICTION)

    val question = props.question
    // false = your estimate open, true = group estimate open
    // Open group prediction by default. If the user has no permission to predict, your estimate tab is not useful.
    var groupPredictionOpen by useState(!hasPredictPermission)
    var groupHistogramOpen by useState(false)

    var pendingPrediction: ProbabilityDistribution? by useState(null) // to be submitted
    var predictionPreview: ProbabilityDistribution? by useState(null) // continuously updated preview
    var pendingPredictionState by useState(PendingPredictionState.NONE)

    val predictionTerm = question.predictionTerminology.term
    val predictionTerms = question.predictionTerminology.plural

    useDebounce(5000, pendingPredictionState.toString()) {
        if (pendingPredictionState in listOf(PendingPredictionState.ACCEPTED, PendingPredictionState.ERROR))
            pendingPredictionState = PendingPredictionState.NONE
    }

    useDebounce(1000, confidoJSON.encodeToString(pendingPrediction)) {
        pendingPrediction?.let { dist ->
            runCoroutine {
                pendingPredictionState = PendingPredictionState.SENDING
                Client.sendData("${question.urlPrefix}/predict", dist, onError = {
                    pendingPredictionState = PendingPredictionState.ERROR
                }) {
                    pendingPredictionState = PendingPredictionState.ACCEPTED
                }
                pendingPrediction = null
            }
        }
    }
    useOnUnmount(pendingPrediction) { runCoroutine { Client.sendData("${question.urlPrefix}/predict", it, onError = {showError(it)}) {} } }


    // Tabs
    Stack {
        css {
            padding = if (layoutMode >= LayoutMode.TABLET) Padding(15.px, 0.px) else 15.px
            background = bgColor
            borderRadius = 5.px
            flexShrink = number(0.0)
        }
        direction = FlexDirection.row
        QuestionEstimateTabButton {
            +"Your $predictionTerm"
            active = !groupPredictionOpen
            onClick = { groupPredictionOpen = false }
        }
        QuestionEstimateTabButton {
            +"Group $predictionTerm"
            active = groupPredictionOpen
            onClick = { groupPredictionOpen = true }
        }
    }

    if (!groupPredictionOpen) {
        div {
            css {
                backgroundColor = Color("#fff")
                flexShrink = number(0.0)
            }
            div {
                key = "myPredictionBox"
                if (pendingPredictionState != PendingPredictionState.NONE && pendingPredictionState != PendingPredictionState.MAKING) {
                    div {
                        key = "submitFeedback"
                        css {
                            position = Position.absolute
                            zIndex = integer(10)
                            borderRadius = 5.px
                            left = 50.pct
                            transform = translatex(-50.pct)
                            top = 12.px
                            padding = Padding(4.px, 6.px)
                            background = rgba(0, 0, 0, 0.7)
                            color = NamedColor.white
                            fontWeight = integer(500)
                            fontSize = 12.px
                            lineHeight = 15.px
                            fontFamily = sansSerif
                        }
                        val word = question.predictionTerminology.name.lowercase()
                        +when (pendingPredictionState) {
                            PendingPredictionState.MAKING -> "${word.capFirst()} submit pending"
                            PendingPredictionState.SENDING -> "Submitting ${word.uncapFirst()}..."
                            PendingPredictionState.ACCEPTED -> "${word.capFirst()} submitted"
                            PendingPredictionState.ERROR -> "Error submitting ${word.uncapFirst()}"
                            else -> ""
                        }
                    }
                }
                css {
                    position = Position.relative
                }

                // We render the prediction input even when the question is closed as long as a prediction
                // has been made, this allows the user to see the ranges selected on the slider.
                if ((question.open || props.myPrediction != null) && hasPredictPermission) {
                    PredictionInput {
                        key = "predictionInput"
                        space = props.question.answerSpace
                        this.dist = props.myPrediction?.dist
                        this.disabled = !question.open || !hasPredictPermission
                        this.question = question
                        if (question.resolutionVisible)
                            this.resolution = question.resolution
                        if (question.open) {
                            this.onChange = { newDist, isCommit->
                                if (isCommit) {
                                    pendingPredictionState = PendingPredictionState.MAKING
                                    pendingPrediction = newDist
                                    predictionPreview = null
                                } else {
                                    pendingPredictionState = PendingPredictionState.NONE
                                    predictionPreview = newDist
                                    pendingPrediction = null
                                }
                            }
                        }
                    }
                } else {
                    // The question is closed and no prediction has been made, or the user has no permission to predict at all.
                    // We show just the graph instead of a disabled input with a slider.
                    PredictionGraph {
                        key = "groupPredictionBox"
                        space = props.question.answerSpace
                        dist = null
                        isGroup = false
                    }
                    PredictionOverlay {
                        if (!hasPredictPermission) {
                            +when (props.question.predictionTerminology) {
                                PredictionTerminology.PREDICTION -> "You are not allowed to make predictions with your role."
                                PredictionTerminology.ANSWER -> "You are not allowed to answer with your role."
                                PredictionTerminology.ESTIMATE -> "You are not allowed to make estimates with your role."
                            }
                        } else {
                            +when (props.question.predictionTerminology) {
                                PredictionTerminology.PREDICTION -> "You did not make any predictions."
                                PredictionTerminology.ANSWER -> "You did not answer this question."
                                PredictionTerminology.ESTIMATE -> "You did not make any estimates."
                            }
                        }
                    }
                }
            }
        }
        MyPredictionDescription {
            this.dist = predictionPreview ?: pendingPrediction ?: props.myPrediction?.dist
            this.resolved = props.resolution != null
        }
    } else {
        if (groupHistogramOpen) {
            // Group prediction histogram
            BinaryHistogramSection {
                this.question = question
                onHide = { groupHistogramOpen = false }
            }
        } else {
            // Group prediction graph
            div {
                css {
                    backgroundColor = Color("#fff")
                    flexShrink = number(0.0)
                    position = Position.relative
                }
                PredictionGraph {
                    key = "groupPredictionBox"
                    space = props.question.answerSpace
                    dist = props.groupPrediction?.dist
                    resolution = props.resolution
                    isGroup = true
                    this.question = props.question
                    onHistogramButtonClick = { groupHistogramOpen = !groupHistogramOpen }
                }
                val canViewAll = appState.hasPermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
                if (question.groupPredictionVisibility == GroupPredictionVisibility.ANSWERED && props.myPrediction == null && !canViewAll) {
                    PredictionOverlay {
                        +"You will be able to see the group $predictionTerm once you add your own."
                    }
                } else if (question.groupPredictionVisibility == GroupPredictionVisibility.MODERATOR_ONLY && !canViewAll) {
                    PredictionOverlay {
                        +"The group $predictionTerm is currently only visible to moderators."
                    }
                } else if (props.numPredictors == 0) {
                    PredictionOverlay {
                        if (props.question.open) {
                            +"There are no $predictionTerms yet."
                        } else {
                            +"No $predictionTerms were made."
                        }
                    }
                }
            }
            GroupPredictionDescription {
                this.prediction = props.groupPrediction
                this.myPredictionExists = props.myPrediction != null
                this.resolved = props.resolution != null
                this.numPredictors = props.numPredictors
                this.predictionTerminology = props.question.predictionTerminology
            }
        }
    }
}

private val BinaryHistogramSection = FC<BinaryHistogramSectionProps> { props ->
    val histogram = useWebSocket<BinaryHistogram>("/state${props.question.urlPrefix}/histogram")
    div {
        css {
            backgroundColor = Color("#fff")
            flexShrink = number(0.0)
            position = Position.relative
        }
        BinaryPredictionHistogram {
            key = "binaryPredictionHistogram"
            this.question = question
            this.binaryHistogram = histogram.data
        }
        GraphButtonContainer {
            GraphButton {
                this.palette = MainPalette.primary
                CirclesIcon {}
                onClick = { props.onHide?.invoke() }
            }
        }
    }
    histogram.data?.let {
        BinaryHistogramDescription {
            this.resolved = props.question.state == QuestionState.RESOLVED
            this.binaryHistogram = it
            this.predictionTerminology = props.question.predictionTerminology
        }
    }
}

private val ShortenedTextExpanderClass = emotion.css.ClassName {
    textDecoration = None.none
    color = MainPalette.primary.color
    cursor = Cursor.pointer
    hover {
        filter = brightness(50.pct)
    }
}

private val QuestionHeader = FC<QuestionHeaderProps> { props ->
    val layoutMode = useContext(LayoutModeContext)
    Stack {
        css {
            padding = Padding(20.px, if (layoutMode >= LayoutMode.TABLET) 0.px else 15.px, 5.px)
            gap = 4.px
            background = bgColor
        }

        // Question text
        div {
            css {
                fontFamily = sansSerif
                fontWeight = integer(800)
                fontSize = 34.px
                lineHeight = 105.pct
                color = Color("#000000")
            }
            +props.text
        }

        // Question status
        // TODO: Implement scheduled transitions on backend
        //QuestionStatusLine {
        //    text = "Opened 10 feb 2023, 12:00"
        //}
        //QuestionStatusLine {
        //    text = "Closing 26 feb 2023, 12:00"
        //}
        //QuestionStatusLine {
        //    text = "Resolving 28 feb 2023, 12:00"
        //}
        QuestionStatusLine {
            text = props.questionState.pastVerb.lowercase().capFirst()
            props.stateHistory.filter { it.newState == props.questionState }.maxByOrNull { it.at }?.let {
                time = it.at
            }
        }

        if (props.isHidden) {
            QuestionStatusLine {
                text = "This question is hidden from participants"
            }
        }

        // Resolution
        props.resolution?.let {
            div {
                css {
                    fontFamily = sansSerif
                    fontWeight = FontWeight.bold
                    fontSize = 34.px
                    lineHeight = 100.pct
                    // The "No" resolution value uses a red color. All other resolutions are green.
                    color = if (it.value == false) {
                        Color("#FF5555")
                    } else {
                        Color("#00CC2E")
                    }
                }
                +it.format()
            }
        }

        // Question description
        div {
            css {
                padding = Padding(10.px, 0.px, 0.px)
                fontFamily = sansSerif
                fontSize = 15.px
                lineHeight = 18.px
                color = Color("#000000")
            }
            SeeMore {
                maxLines = 3
                lineHeight = 18.0
                linkCss = ShortenedTextExpanderClass
                backgroundColor = UIGrayBg

                TextWithLinks {
                    text = props.description
                }
            }
        }
    }
}

private val QuestionCommentSection = FC<QuestionCommentSectionProps> { props ->
    val comments = useWebSocket<Map<String, CommentInfo>>("/state${props.question.urlPrefix}/comments")
    val layoutMode = useContext(LayoutModeContext)

    var addCommentOpen by useState(false)
    var sortType by useState(SortType.NEWEST)

    AddCommentDialog {
        open = addCommentOpen
        onClose = { addCommentOpen = false }
        id = props.question.id
        variant = CommentInputVariant.QUESTION
        prediction = props.myPrediction
    }

    Stack {
        css {
            gap = 27.px
            if (layoutMode >= LayoutMode.TABLET) {
                marginBottom = 27.px
            }
        }

        DividerHeading {
            text = "Comments"
            if (comments.data?.isNotEmpty() == true) {
                SortButton {
                    css {
                        alignSelf = AlignSelf.end
                    }
                    options = listOf(SortType.NEWEST, SortType.OLDEST)
                    this.sortType = sortType
                    onChange = { sort -> sortType = sort }
                }
            }
        }

        if (layoutMode >= LayoutMode.TABLET) {
            AddCommentField {
                id = props.question.id
                variant = CommentInputVariant.QUESTION
                prediction = props.myPrediction
            }
        }
    }

    Stack {
        css {
            flexGrow = number(1.0)
            gap = 8.px
            marginBottom = 44.px
        }

        when (comments) {
            is WSData -> {
                val sortedComments = when (sortType) {
                    SortType.NEWEST -> comments.data.entries.sortedByDescending { it.value.comment.timestamp }
                    SortType.OLDEST -> comments.data.entries.sortedBy { it.value.comment.timestamp }
                    else -> emptyList()
                }
                sortedComments.map {
                    Comment {
                        commentInfo = it.value
                        key = it.key
                    }
                }
            }

            else -> {
                div {
                    css {
                        padding = Padding(5.px, 15.px)
                        fontFamily = sansSerif
                        fontSize = 15.px
                    }
                    +"Loading the discussion..."
                }
            }
        }
    }

    if (layoutMode == LayoutMode.PHONE && props.allowAddingComment) {
        AddCommentButton {
            onClick = { addCommentOpen = true }
        }
    }
}

private val QuestionStatusLine = FC<QuestionStatusProps> { props ->
    div {
        css {
            fontFamily = sansSerif
            fontWeight = integer(700)
            fontSize = 12.px
            lineHeight = 15.px
            color = Color("rgba(0, 0, 0, 0.3)")

            padding = Padding(2.px, 0.px)
            textTransform = TextTransform.uppercase
        }
        +props.text
        props.time?.let {
            val localTime = it.toLocalDateTime(TimeZone.currentSystemDefault())
            val day = localTime.dayOfMonth
            val month = localTime.month.name.substring(0, 4);
            val year = localTime.year
            val hour = localTime.hour
            val minute = localTime.minute.toString().padStart(2, '0')
            // TODO: 12-hour time?
            +" $day $month $year, $hour:$minute"
        }
    }
}

