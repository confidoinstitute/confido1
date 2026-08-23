package tools.confido.web.components.redesign.questions

import tools.confido.web.ApiError
import tools.confido.question.BinaryHistogram
import tools.confido.web.Client
import web.window.window
import tools.confido.web.components.*
import tools.confido.web.components.redesign.*
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.comments.*
import tools.confido.web.components.redesign.comments.Comment
import tools.confido.web.components.redesign.comments.CommentInputVariant
import tools.confido.web.components.redesign.forms.*
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.components.redesign.questions.dialog.*
import tools.confido.web.components.redesign.questions.dialog.EditQuestionDialog
import tools.confido.web.components.redesign.questions.predictions.*
import tools.confido.web.components.redesign.rooms.*
import tools.confido.web.components.redesign.rooms.dialog.CsvExportDialog
import web.cssom.*
import emotion.css.ClassName
import emotion.react.*
import tools.confido.web.hooks.*
import js.objects.unsafeJso as jso
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.encodeToDynamic
import tools.confido.payloads.responses.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.router.dom.Link
import react.router.useLocation
import react.router.useNavigate
import tools.confido.rooms.*
import tools.confido.api.Api
import tools.confido.distributions.*
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.serialization.*
import tools.confido.spaces.*
import tools.confido.web.components.redesign.presenter.PresenterContext
import tools.confido.state.BinaryHistogramPV
import tools.confido.state.appConfig
import tools.confido.utils.*
import tools.confido.web.utils.*
import tools.confido.state.Topics

val QuestionContext = createContext<Question?>(null)
enum class PendingPredictionState {
    NONE,
    MAKING,
    SENDING,
    ACCEPTED,
    ERROR,
}
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
    var onPrev: (()->Unit)?
    var onNext: (()->Unit)?
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

external interface QuestionPageExtraProps: Props {
    var question: Question
    var place: ClientExtension.QuestionPagePlace
}

val QuestionPageExtra = FC<QuestionPageExtraProps> { props->
    ClientExtension.forEach { it.questionPageExtra(props.question, props.place, this) }
}

val QuestionPage = FC<QuestionLayoutProps>("QuestionPage") { props ->
    val room = use(RoomContext)!!
    val counts = useQuestionCounts(props.question.ref)
    val myPrediction = useMyPredictions()[props.question.ref]
    val groupPrediction = useSubscription(Topics.groupPred(props.question.ref))
    val layoutMode = use(LayoutModeContext)!!
    val roomPalette = room.color.palette

    val idx = room.questions.indexOf(props.question.ref)
    console.log("QIDX $idx")
    // order in room.questions is reversed
    val prevQ = room.questions.getOrNull(idx+1)
    val nextQ = room.questions.getOrNull(idx-1)

    var quickSettingsOpen by useState(false)
    var resolutionDialogOpen by useState(props.openResolve ?: false)
    var csvDialogOpen by useState(false)
    val loc = useLocation()
    val navigate = useNavigate()

    val extContext = ClientExtension.getContextValues(ExtensionContextPlace.QUESTION_PAGE)

    useEffectWithCleanup(props.question.id) { // TODO does not reflect change in question value
        window.asDynamic().curQuestion = confidoJSON.encodeToDynamic(props.question)
        onCleanup {
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

    if (!room.hasPermission(useMyUser(), RoomPermission.VIEW_QUESTIONS))
    // TODO proper no permission page
        return@FC

    QuestionContext.Provider {
    value = props.question
    ClientExtension.contexts[ExtensionContextPlace.QUESTION_PAGE]!!.Provider {
    value = extContext
        QuestionQuickSettingsDialog {
            question = props.question
            open = quickSettingsOpen
            canEdit = room.hasPermission(useMyUser(), RoomPermission.MANAGE_QUESTIONS)
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
                this.onNext = nextQ?.let { { navigate(room.urlPrefix + questionUrl(it.id) )  } }
                this.onPrev = prevQ?.let { { navigate(room.urlPrefix + questionUrl(it.id) )  } }
            }
            QuestionPredictionSection {
                this.question = props.question
                if (props.question.state == QuestionState.RESOLVED) {
                    this.resolution = props.question.resolution
                }
                this.myPrediction = myPrediction
                this.numPredictors = counts.predictors
                this.groupPrediction = groupPrediction.data
            }
            if (props.question.allowComments && usePermission(room, RoomPermission.VIEW_QUESTION_COMMENTS)) {
                    QuestionCommentSection {
                        this.question = props.question
                        this.myPrediction = myPrediction
                        this.allowAddingComment = usePermission(room, RoomPermission.POST_QUESTION_COMMENT) && props.question.allowComments
                    }
                }
            }
            QuestionPageExtra { question = props.question; place = ClientExtension.QuestionPagePlace.QUESTION_PAGE_END }
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
            backgroundColor = rgb(255, 255, 255, 0.75)
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
    val layoutMode = use(LayoutModeContext)!!
    val room = use(RoomContext)!!
    val hasPredictPermission = usePermission(room, RoomPermission.SUBMIT_PREDICTION)

    val question = props.question
    // false = your estimate open, true = group estimate open
    // Open group prediction by default. If the user has no permission to predict, your estimate tab is not useful.
    var groupPredictionOpen by useState(!hasPredictPermission)
    var groupHistogramOpen by useState(false)

    // Timeline scrub ghost (numeric questions): hovering the history timeline
    // overlays the historical PDF on the group graph; clicking pins the *moment*
    // (the cursor stays where clicked; re-click near the pin or Escape unpins).
    // The displayed time is the raw instant, but distributions are fetched and
    // cached per resolved update — belief is a step function, so a scrub across
    // a whole segment costs one fetch, not one per pixel.
    var scrubMoment by useState<TimelineMoment?>(null)
    var pinnedMoment by useState<TimelineMoment?>(null)
    var ghost by useState<ProbabilityDistribution?>(null)
    val ghostCache = useRef<MutableMap<Int, ProbabilityDistribution?>>(mutableMapOf())
    val ghostReq = useRef<Int>(0)
    val activeGhostMoment = scrubMoment ?: pinnedMoment
    // Binary questions pin too (as the marker-creation anchor), but have no ghost.
    val hasGhost = question.answerSpace is NumericSpace

    // Compare view (aggregate distributions at two moments; desktop only, the
    // timeline hides its entry point on phones).
    var compareRange by useState<Pair<Int, Int?>?>(null)  // older ts, newer ts (null = now)
    var compareDists by useState<Pair<ProbabilityDistribution?, ProbabilityDistribution?>?>(null)
    val compareReq = useRef("")
    useEffect(compareRange?.let { "${it.first}:${it.second}" } ?: "") {
        val cr = compareRange ?: run { compareDists = null; return@useEffect }
        val key = "${cr.first}:${cr.second}"
        compareReq.current = key
        runCoroutine {
            val a = try { Client.call(Api.questionGroupPredAt(question.ref, cr.first))?.dist } catch (e: ApiError) { null }
            val b = cr.second?.let {
                try { Client.call(Api.questionGroupPredAt(question.ref, it))?.dist } catch (e: ApiError) { null }
            } ?: props.groupPrediction?.dist
            if (compareReq.current == key) compareDists = a to b
        }
    }
    useEffect(activeGhostMoment?.point?.ts) {
        val ts = activeGhostMoment?.point?.ts?.takeIf { hasGhost }
        if (ts == null) { ghost = null; return@useEffect }
        ghostReq.current = ts
        val cache = ghostCache.current!!
        if (cache.containsKey(ts)) { ghost = cache[ts] }
        else runCoroutine {
            val dist = try {
                Client.call(Api.questionGroupPredAt(question.ref, ts))?.dist
            } catch (e: ApiError) { null }
            cache[ts] = dist
            if (ghostReq.current == ts) ghost = dist
        }
    }

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
                try {
                    Client.call(Api.questionPredict(question.ref), dist)
                    pendingPredictionState = PendingPredictionState.ACCEPTED
                } catch (e: ApiError) {
                    pendingPredictionState = PendingPredictionState.ERROR
                }
                pendingPrediction = null
            }
        }
    }
    useOnUnmount(pendingPrediction) { runCoroutine { Client.call(Api.questionPredict(question.ref), it) } }


    // Tabs
    Stack {
        css(ClassName("question-tabs")) {
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
            css(ClassName("prediction-box")) {
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
                            background = rgb(0, 0, 0, 0.7)
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
                        // Immediate-commit control: predictions send on interaction,
                        // so the whole input gates on the connection.
                        this.disabled = !question.open || !hasPredictPermission || useDisconnected()
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
            this.question = question
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
                css(ClassName("prediction-box")) {
                    backgroundColor = Color("#fff")
                    flexShrink = number(0.0)
                    position = Position.relative
                }
                if (compareRange != null) {
                    // A compared moment that coincides with a marker goes by the
                    // marker's name (plus its time), not the bare timestamp.
                    fun momentLabel(ts: Int): String {
                        val name = (room.timelineMarks + question.timelineMarks)
                            .firstOrNull { it.ts == ts }?.name
                        val dt = formatUpdateDateTime(ts)
                        return if (name != null) "$name · $dt" else dt
                    }
                    CompareDistributions {
                        key = "groupPredictionCompare"
                        space = props.question.answerSpace
                        distA = compareDists?.first
                        labelA = momentLabel(compareRange!!.first)
                        distB = compareDists?.second
                        labelB = compareRange!!.second?.let { momentLabel(it) } ?: "now"
                        onClose = { compareRange = null }
                    }
                } else PredictionGraph {
                    key = "groupPredictionBox"
                    space = props.question.answerSpace
                    dist = props.groupPrediction?.dist
                    resolution = props.resolution
                    isGroup = true
                    this.question = props.question
                    onHistogramButtonClick = { groupHistogramOpen = !groupHistogramOpen }
                    ghostDist = ghost
                    ghostLabel = activeGhostMoment?.let { "as of " + formatUpdateDate(it.ts) }
                    if (scrubMoment == null && pinnedMoment != null)
                        onGhostDismiss = { pinnedMoment = null }
                }
                // History timeline; renders only once there are enough updates. Lives
                // inside the prediction box so the visibility overlays below cover it
                // (the topic is server-gated by the same rule regardless).
                GroupPredictionTimeline {
                    key = "groupPredictionTimeline"
                    this.question = props.question
                    resolution = props.resolution
                    // Pinning works for every question type (it anchors marker
                    // creation); the hover scrub only feeds the numeric ghost.
                    onPin = { m -> pinnedMoment = m }
                    pinnedTs = pinnedMoment?.ts
                    onCompare = { a, b -> compareRange = a to b }
                    if (hasGhost) {
                        onHover = { scrubMoment = it }
                    }
                }
                val canViewAll = usePermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
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
    val histogram = useSubscription(Topics.histogram(props.question.ref))
    val layoutMode = use(LayoutModeContext)!!
    val presenterCtl = use(PresenterContext)!!
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
            if (layoutMode >= LayoutMode.TABLET) {
                GraphButton {
                    this.palette = MainPalette.secondary
                    PresenterIcon {}
                    onClick = { presenterCtl.offer(BinaryHistogramPV(props.question.ref, false)) }
                }
            }
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

val ShortenedTextExpanderClass = emotion.css.ClassName {
    textDecoration = None.none
    color = MainPalette.primary.color
    cursor = Cursor.pointer
    hover {
        filter = brightness(50.pct)
    }
}

private val QuestionHeader = FC<QuestionHeaderProps> { props ->
    val layoutMode = use(LayoutModeContext)!!
    Stack {
        css(ClassName("question-header")) {
            padding = Padding(20.px, if (layoutMode >= LayoutMode.TABLET) 0.px else 15.px, 5.px)
            gap = 4.px
            background = bgColor
        }

        if (layoutMode == LayoutMode.PHONE && (props.onNext != null || props.onPrev != null)) {
            Stack {
                direction = FlexDirection.row
                css {
                    justifyContent = if (props.onPrev == null)  JustifyContent.end
                        else if (props.onNext == null) JustifyContent.start
                        else JustifyContent.spaceBetween
                }
                val btnCss = ClassName {
                    fontVariant = FontVariant.smallCaps
                    color = Color("#666")
                }
                if (props.onPrev != null)
                ButtonUnstyled {
                    className = btnCss
                    +"← Previous"
                    onClick = { props.onPrev?.invoke() }
                }
                if (props.onNext != null)
                    ButtonUnstyled {
                        className = btnCss
                        +"Next →"
                        onClick = { props.onNext?.invoke() }
                    }
            }
        }

        // Question text
        Stack {
            direction = FlexDirection.row
            css {
                if (layoutMode >= LayoutMode.TABLET) {
                    if (props.onPrev != null) marginLeft = -30.px
                    if (props.onNext != null) marginRight = -30.px
                }
            }
            if (props.onPrev != null && layoutMode >= LayoutMode.TABLET) {
                div {
                    css {
                        flexGrow = number(0.0)
                        alignSelf = AlignSelf.center
                        width = 30.px
                        flexBasis = 30.px
                    }
                    IconButton {
                        BackIcon{}
                        onClick = { props.onPrev?.invoke() }
                    }
                }
            }
            div {
                css(ClassName("question-title")) {
                    fontFamily = sansSerif
                    fontWeight = integer(800)
                    fontSize = 34.px
                    lineHeight = 105.pct
                    color = Color("#000000")
                    flexGrow = number(1.0)
                }
                +props.text
            }
            if (props.onNext != null && layoutMode >= LayoutMode.TABLET) {
                div {
                    css {
                        flexGrow = number(0.0)
                        alignSelf = AlignSelf.center
                        width = 30.px
                        flexBasis = 30.px
                    }
                    IconButton {
                        FwdIcon{}
                        onClick = { props.onNext?.invoke() }
                    }
                }
            }
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

                whiteSpace = WhiteSpace.preLine
            }
            SeeMore {
                maxLines = 3
                lineHeight = 18.0
                linkCss = ShortenedTextExpanderClass
                backgroundColor = UIGrayBg

                //TextWithLinks {
                //    text = props.description
                //}
                MarkdownText {
                    source = props.description
                }
            }
        }
    }
}

private val QuestionCommentSection = FC<QuestionCommentSectionProps> { props ->
    val comments = useSubscription(Topics.questionComments(props.question.ref))
    val layoutMode = use(LayoutModeContext)!!

    var addCommentOpen by useState(false)
    var sortType by useState(SortType.NEWEST)
    val room = use(RoomContext)!!

    var spoilerExpanded by useState(false)

    val isFirstByMe = comments.data?.none { it.isMine } ?: true
    val effectiveTemplate = if (isFirstByMe) room.commentTemplate else ""

    if (comments.data != null)
    AddCommentDialog {
        open = addCommentOpen
        onClose = { addCommentOpen = false }
        id = props.question.id
        variant = CommentInputVariant.QUESTION
        prediction = props.myPrediction
        template = effectiveTemplate
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

        if (layoutMode >= LayoutMode.TABLET && comments.data != null) {
            AddCommentField {
                id = props.question.id
                variant = CommentInputVariant.QUESTION
                prediction = props.myPrediction
                template = effectiveTemplate
            }
        }
    }

    Stack {
        css {
            flexGrow = number(1.0)
            gap = 8.px
            marginBottom = 44.px
        }

        val canViewAll = usePermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
        val lastPrediction = useMyPredictions()[props.question.ref]
        val canView = when (props.question.commentVisibility) {
            CommentVisibility.EVERYONE -> true
            CommentVisibility.ANSWERED -> lastPrediction != null
            CommentVisibility.MODERATOR_ONLY -> false
        }
        val didComment = comments.data?.any  {  it.isMine } ?: false
        val spoiler = canView && appConfig.commentSpoiler && !(didComment && lastPrediction != null)


        if (!canViewAll && !canView) {
            div {
                css {
                    padding = Padding(5.px, 15.px)
                    fontFamily = sansSerif
                    fontSize = 15.px
                    color = Color("#666666")
                }
                +when (props.question.commentVisibility) {
                    CommentVisibility.ANSWERED -> "You will be able to see others' comments once you add your prediction."
                    CommentVisibility.MODERATOR_ONLY -> "Only moderators can see others' comments."
                    else -> ""
                }
            }
        }

        when (comments) {
            is WSData -> {
                val sortedComments = when (sortType) {
                    SortType.NEWEST -> comments.data.sortedByDescending { it.timestamp }
                    SortType.OLDEST -> comments.data.sortedBy { it.timestamp }
                    else -> emptyList()
                }

                if (spoiler && !spoilerExpanded && sortedComments.size > 0)
                    Link {
                        css {
                            textAlign = TextAlign.center
                            fontWeight = FontWeight.bold
                        }
                        +"Show ${sortedComments.size} comments"
                        onClick = {
                            spoilerExpanded  = true
                        }
                    }
                else
                sortedComments.map {
                    Comment {
                        comment = it
                        key = it.commentId.id
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

        if (!canViewAll && !canView && comments is WSData && comments.data.isNotEmpty() ) {
            div {
                css {
                    padding = Padding(5.px, 15.px)
                    fontFamily = sansSerif
                    fontSize = 15.px
                    color = Color("#666666")
                }
                +when (props.question.commentVisibility) {
                    CommentVisibility.ANSWERED -> "Add your prediction to see others' comments."
                    CommentVisibility.MODERATOR_ONLY -> "Only moderators can see others' comments."
                    else -> ""
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
        css(ClassName("question-status")) {
            fontFamily = sansSerif
            fontWeight = integer(700)
            fontSize = 12.px
            lineHeight = 15.px
            color = Color("rgba(0, 0, 0, 0.55)")

            padding = Padding(2.px, 0.px)
            textTransform = TextTransform.uppercase
        }
        +props.text
        props.time?.let {
            val localTime = it.toLocalDateTime(TimeZone.currentSystemDefault())
            val day = localTime.dayOfMonth
            val month = localTime.month.name.substring(0, 3);
            val year = localTime.year
            val hour = localTime.hour
            val minute = localTime.minute.toString().padStart(2, '0')
            // TODO: 12-hour time?
            +" $day $month $year, $hour:$minute"
        }
    }
}
