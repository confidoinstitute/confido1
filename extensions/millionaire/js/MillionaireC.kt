package extensions.millionaire

import Client
import components.AppStateContext
import components.presenter.PresenterPage
import components.presenter.PresenterPageProps
import components.presenter.QuestionPP
import components.presenter.presenterPageMap
import components.redesign.basic.showError
import components.redesign.forms.Button
import components.redesign.presenter.PresenterContext
import components.redesign.questions.QuestionContext
import components.redesign.questions.predictions.MyPredictionDescriptionProps
import components.redesign.questions.predictions.yesNoColored
import components.redesign.rooms.RoomContext
import csstype.PropertiesBuilder
import emotion.css.*
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import hooks.useCoroutineLock
import hooks.useWebSocket
import payloads.responses.WSData
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul
import react.router.useNavigate
import rooms.Room
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.state.QuestionPV
import users.User
import web.cssom.*
import web.timers.clearInterval
import web.timers.setInterval
import kotlin.js.Date
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

// =============================================================================
// WWTBAM Color Palette
// =============================================================================

private object MilColors {
    val bgCenter = Color("#0d0d3b")
    val bgEdge = Color("#050520")
    val gold = Color("#ffd700")
    val goldDark = Color("#c87800")
    val goldDim = Color("#7a6520")
    val textPrimary = Color("#ffffff")
    val textSecondary = Color("#8090b0")
    val stepDefault = Color("rgba(20, 40, 80, 0.7)")
    val stepCompleted = Color("rgba(30, 80, 45, 0.6)")
    val stepCurrent = Color("#c87800")
    val stepCurrentEnd = Color("#ffd700")
    val scorePositive = Color("#44dd66")
    val scoreNegative = Color("#ff4444")
    val timerGreen = Color("#00cc44")
    val timerRed = Color("#cc0000")
    val leaderGold = Color("rgba(255, 215, 0, 0.15)")
}

// =============================================================================
// Keyframe Animations
// =============================================================================

private val pulseGlow = keyframes {
    0.pct { filter = brightness(100.pct) }
    50.pct { filter = brightness(130.pct) }
    100.pct { filter = brightness(100.pct) }
}

private val timerExpiredFlash = keyframes {
    0.pct { opacity = number(1.0) }
    50.pct { opacity = number(0.3) }
    100.pct { opacity = number(1.0) }
}

private val fadeIn = keyframes {
    0.pct { opacity = number(0.0) }
    100.pct { opacity = number(1.0) }
}

// =============================================================================
// Timer Bar Component
// =============================================================================

external interface MillionaireTimerBarProps : Props {
    var roomId: String
}

val MillionaireTimerBar = FC<MillionaireTimerBarProps> { props ->
    val timerWS = useWebSocket<TimerState?>("/api${Room.urlPrefix(props.roomId)}/ext/millionaire/timer.ws")
    var fraction by useState(1.0)

    val timerState = (timerWS as? WSData)?.data

    useEffectWithCleanup(timerState?.startedAt, timerState?.durationMs, timerState?.running) {
        if (timerState == null || !timerState.running) {
            fraction = if (timerState == null) 1.0 else 0.0
            return@useEffectWithCleanup
        }

        fun update() {
            val now = Date.now()
            val elapsed = now - timerState.startedAt
            val remaining = max(0.0, 1.0 - elapsed / timerState.durationMs)
            fraction = remaining
        }
        update()

        val interval = setInterval(50.milliseconds) { update() }
        onCleanup { clearInterval(interval) }
    }

    if (timerState == null) return@FC

    val expired = fraction <= 0.0
    // Hue: 120 (green) -> 60 (yellow) -> 0 (red)
    val hue = (fraction * 120).roundToInt()

    div {
        css {
            width = 100.pct
            height = 12.px
            borderRadius = 6.px
            backgroundColor = Color("rgba(255,255,255,0.1)")
            overflow = Overflow.hidden
            marginTop = 16.px
        }
        div {
            css {
                height = 100.pct
                width = (fraction * 100).pct
                backgroundColor = Color("hsl($hue, 100%, 45%)")
                borderRadius = 6.px
                transitionProperty = "width".asDynamic()
                transitionDuration = 0.05.s
                transitionTimingFunction = TransitionTimingFunction.linear
                if (expired) {
                    animationName = timerExpiredFlash
                    animationDuration = 0.5.s
                    animationIterationCount = AnimationIterationCount.infinite
                    backgroundColor = MilColors.timerRed
                    width = 100.pct
                }
            }
        }
    }
}

// =============================================================================
// Prize Ladder Sidebar Component
// =============================================================================

external interface PrizeLadderProps : Props {
    var ladder: List<Int>
    var currentIndex: Int
    var totalQuestions: Int
}

val PrizeLadder = FC<PrizeLadderProps> { props ->
    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            justifyContent = JustifyContent.center
            gap = 4.px
            padding = Padding(16.px, 8.px)
            height = 100.pct
        }
        // Display from highest (top) to lowest (bottom)
        val displayLadder = props.ladder.mapIndexed { idx, prize -> idx to prize }.reversed()
        for ((idx, prize) in displayLadder) {
            val isCurrent = idx == props.currentIndex
            val isCompleted = idx < props.currentIndex
            val isFuture = idx > props.currentIndex

            div {
                css {
                    position = Position.relative
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.spaceBetween
                    padding = Padding(8.px, 24.px)
                    fontSize = if (isCurrent) 2.8.vh else 2.2.vh
                    fontWeight = if (isCurrent) FontWeight.bold else FontWeight.normal
                    color = when {
                        isCurrent -> MilColors.textPrimary
                        isCompleted -> Color("rgba(255,255,255,0.6)")
                        else -> Color("rgba(255,255,255,0.4)")
                    }
                    if (isCurrent) {
                        // Black outline so white text stays readable during bright pulse phases
                        textShadow = "0 0 4px rgba(0,0,0,0.8), 0 0 8px rgba(0,0,0,0.4)".unsafeCast<TextShadow>()
                    }
                    clipPath = "polygon(6% 0%, 94% 0%, 100% 50%, 94% 100%, 6% 100%, 0% 50%)".unsafeCast<ClipPath>()

                    when {
                        isCurrent -> {
                            background = linearGradient(
                                90.deg,
                                stop(MilColors.stepCurrent, 0.pct),
                                stop(MilColors.stepCurrentEnd, 100.pct),
                            )
                            animationName = pulseGlow
                            animationDuration = 2.s
                            animationIterationCount = AnimationIterationCount.infinite
                            animationTimingFunction = AnimationTimingFunction.easeInOut
                        }
                        isCompleted -> {
                            backgroundColor = MilColors.stepCompleted
                        }
                        else -> {
                            backgroundColor = MilColors.stepDefault
                        }
                    }

                    minHeight = 3.5.vh
                    lineHeight = number(1.2)
                }
                span {
                    css {
                        opacity = number(if (isFuture) 0.5 else 0.8)
                        marginRight = 12.px
                    }
                    +"${idx + 1}"
                }
                span {
                    css {
                        fontFamily = string("monospace")
                        letterSpacing = 0.5.px
                        if (isCurrent) {
                            textShadow = "0 0 8px rgba(255,215,0,0.6), 0 0 3px rgba(0,0,0,0.9)".unsafeCast<TextShadow>()
                        }
                    }
                    +MillionaireConfig.formatPrize(prize)
                }
            }
        }
    }
}

// =============================================================================
// WWTBAM Background Mixin
// =============================================================================

private fun PropertiesBuilder.wwtbamBackground() {
    background = "radial-gradient(ellipse at center, #0d0d3b 0%, #050520 100%)".unsafeCast<Background>()
    // Subtle starburst overlay effect
    after {
        content = string("\"\"")
        position = Position.absolute
        top = 0.px
        left = 0.px
        right = 0.px
        bottom = 0.px
        background = "radial-gradient(ellipse at center, rgba(100,120,200,0.03) 0%, transparent 70%)".unsafeCast<Background>()
        pointerEvents = None.none
    }
}

// =============================================================================
// Ladder Question Presenter Page (WWTBAM themed)
// =============================================================================

val MillionaireLadderQuestionPP = FC<PresenterPageProps<MillionaireLadderQuestionPV>> { props ->
    val room = props.view.room.deref() ?: return@FC
    val st = MillionaireCE.getState(room)
    val question = st.curQuestion ?: return@FC
    val qs = MillionaireCE.questions(room)
    val ladder = MillionaireConfig.prizeLadderForCount(qs.size)
    val currentPrize = ladder.getOrElse(st.curIndex) { ladder.lastOrNull() ?: 0 }

    // Main container
    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.row
            width = 100.vw
            height = 100.vh
            position = Position.relative
            overflow = Overflow.hidden
            wwtbamBackground()
            fontFamily = string("'Inter', 'Segoe UI', 'Helvetica Neue', sans-serif")
        }

        // ---- Left: Question Area (75%) ----
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                flex = Flex(number(1.0), number(1.0), 75.pct)
                padding = Padding(3.vh, 4.vw)
                position = Position.relative
                zIndex = integer(1)
            }

            // Top: Question number and prize value
            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                    gap = 24.px
                    marginBottom = 2.vh
                }
                span {
                    css {
                        fontSize = 3.5.vh
                        color = MilColors.textSecondary
                        fontWeight = FontWeight.bold
                        letterSpacing = 2.px
                        textTransform = TextTransform.uppercase
                    }
                    +"Question ${st.curIndex + 1}"
                }
                span {
                    css {
                        fontSize = 4.5.vh
                        color = MilColors.gold
                        fontWeight = FontWeight.bold
                        textShadow = "0 0 20px rgba(255,215,0,0.5), 0 0 40px rgba(255,215,0,0.2)".unsafeCast<TextShadow>()
                    }
                    +MillionaireConfig.formatPrize(currentPrize)
                }
            }

            // Center: Question text
            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                    flexGrow = number(1.0)
                    animationName = fadeIn
                    animationDuration = 0.5.s
                    animationTimingFunction = AnimationTimingFunction.easeOut
                }
                div {
                    key = question.id
                    css {
                        fontSize = 5.5.vh
                        fontWeight = FontWeight.bold
                        color = MilColors.textPrimary
                        textAlign = TextAlign.center
                        lineHeight = number(1.3)
                        maxWidth = 90.pct
                        textShadow = "0 2px 4px rgba(0,0,0,0.5)".unsafeCast<TextShadow>()
                        animationName = fadeIn
                        animationDuration = 0.6.s
                        animationTimingFunction = AnimationTimingFunction.easeOut
                    }
                    +question.name
                }
                if (question.description.isNotBlank()) {
                    div {
                        css {
                            fontSize = 2.8.vh
                            color = MilColors.textSecondary
                            textAlign = TextAlign.center
                            marginTop = 2.vh
                            maxWidth = 80.pct
                            lineHeight = number(1.4)
                        }
                        +question.description
                    }
                }
            }

            // Bottom: Predictor count + timer
            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    alignItems = AlignItems.center
                    gap = 8.px
                }
                div {
                    css {
                        fontSize = 2.5.vh
                        color = MilColors.textSecondary
                    }
                    +"${question.numPredictors} predictions submitted"
                }

                // Timer bar
                if (MillionaireConfig.timerConfig !is TimerConfig.None) {
                    div {
                        css {
                            width = 80.pct
                        }
                        MillionaireTimerBar {
                            roomId = room.id
                        }
                    }
                }
            }
        }

        // ---- Right: Prize Ladder Sidebar (25%) ----
        div {
            css {
                flex = Flex(number(0.0), number(0.0), 25.pct)
                backgroundColor = Color("rgba(0, 0, 0, 0.3)")
                borderLeft = Border(1.px, LineStyle.solid, Color("rgba(255,215,0,0.2)"))
                position = Position.relative
                zIndex = integer(1)
            }
            PrizeLadder {
                this.ladder = ladder
                currentIndex = st.curIndex
                totalQuestions = qs.size
            }
        }
    }
}

// =============================================================================
// Scoreboard Presenter Page (WWTBAM themed for ladder, plain for multiplicative)
// =============================================================================

val MillionaireScoreboardPP = FC<PresenterPageProps<MillionaireScoreboardPV>> { props ->
    val room = props.view.room
    val scoreboard = useWebSocket<Map<Ref<User>, Double>>("/api${Room.urlPrefix(room.id)}/ext/millionaire/scoreboard.ws")
    if (scoreboard !is WSData) return@FC

    val isLadder = MillionaireConfig.mode == MillionaireMode.LADDER

    if (isLadder) {
        // WWTBAM themed scoreboard
        div {
            css {
                width = 100.vw
                height = 100.vh
                position = Position.relative
                overflow = Overflow.hidden
                wwtbamBackground()
                fontFamily = string("'Inter', 'Segoe UI', 'Helvetica Neue', sans-serif")
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
                padding = Padding(3.vh, 4.vw)
            }

            // Title
            div {
                css {
                    fontSize = 5.vh
                    fontWeight = FontWeight.bold
                    color = MilColors.gold
                    textShadow = "0 0 20px rgba(255,215,0,0.4), 0 0 40px rgba(255,215,0,0.15)".unsafeCast<TextShadow>()
                    letterSpacing = 4.px
                    textTransform = TextTransform.uppercase
                    marginBottom = 3.vh
                    position = Position.relative
                    zIndex = integer(1)
                }
                +"Leaderboard"
            }

            // Scoreboard table
            div {
                css {
                    position = Position.relative
                    zIndex = integer(1)
                    width = 80.pct
                    maxHeight = 80.vh
                    overflow = Overflow.hidden
                    columnFill = Auto.auto
                    columnCount = integer(2)
                    columnGap = 32.px
                }

                val sorted = scoreboard.data.entries.sortedByDescending { it.value }
                val topScore = sorted.firstOrNull()?.value

                sorted.forEachIndexed { idx, entry ->
                    val isLeader = entry.value == topScore && entry.value > 0
                    div {
                        css {
                            display = Display.flex
                            flexDirection = FlexDirection.row
                            justifyContent = JustifyContent.spaceBetween
                            alignItems = AlignItems.center
                            padding = Padding(10.px, 20.px)
                            marginBottom = 6.px
                            borderRadius = 6.px
                            fontSize = 2.8.vh
                            backgroundColor = if (isLeader) MilColors.leaderGold
                                else Color("rgba(255,255,255,0.05)")
                            if (isLeader) {
                                borderLeft = Border(3.px, LineStyle.solid, MilColors.gold)
                            }
                            breakInside = BreakInside.avoid
                        }
                        // Rank + Name
                        span {
                            css { color = MilColors.textSecondary; marginRight = 12.px; minWidth = 3.vh }
                            +"${idx + 1}."
                        }
                        span {
                            css {
                                color = MilColors.textPrimary
                                flexGrow = number(1.0)
                                fontWeight = if (isLeader) FontWeight.bold else FontWeight.normal
                            }
                            +(entry.key.deref()?.nick ?: "(Anonymous)")
                        }
                        // Score
                        span {
                            css {
                                fontFamily = string("monospace")
                                fontWeight = FontWeight.bold
                                fontSize = 3.vh
                                color = if (entry.value >= 0) MilColors.scorePositive else MilColors.scoreNegative
                                if (isLeader) {
                                    textShadow = "0 0 10px rgba(68,221,102,0.4)".unsafeCast<TextShadow>()
                                }
                            }
                            val sign = if (entry.value >= 0) "+" else "-"
                            +"$sign\$${entry.value.absoluteValue.roundToInt()}"
                        }
                    }
                }
            }
        }
    } else {
        // Original plain scoreboard for multiplicative mode
        div {
            css {
                margin = 20.px
                height = 90.vh
                columnFill = Auto.auto
                columnCount = integer(2)
                overflowX = Auto.auto
            }
            ReactHTML.table {
                css {
                    fontSize = 32.px
                    borderCollapse = BorderCollapse.collapse
                    "tr" {
                        borderTop = Border(1.px, LineStyle.solid, NamedColor.black)
                        borderBottom = Border(1.px, LineStyle.solid, NamedColor.black)
                    }
                }
                scoreboard.data.entries.sortedBy { -it.value }.forEach {
                    ReactHTML.tr {
                        ReactHTML.td {
                            +(it.key.deref()?.nick ?: "(Anonymous)")
                        }
                        ReactHTML.td {
                            css { paddingLeft = 32.px }
                            +"$ ${it.value.roundToInt()}"
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Dynamic Millionaire Presenter (delegates to appropriate sub-view)
// =============================================================================

val MillionairePP = FC<PresenterPageProps<MillionairePV>> { props ->
    val room = props.view.room.deref() ?: return@FC
    val ms = MillionaireCE.getState(room)
    PresenterPage { view = ms.presenterView(room) }
}

// =============================================================================
// Client Extension Object
// =============================================================================

object MillionaireCE : ClientExtension, MillionaireExt() {
    override fun registerPresenterPages() = mapOf(
        presenterPageMap(MillionaireScoreboardPP),
        presenterPageMap(MillionairePP),
        presenterPageMap(MillionaireLadderQuestionPP),
    )

    // --- Auto-navigate to next question ---

    override fun questionPageExtra(q: Question, place: ClientExtension.QuestionPagePlace, cb: ChildrenBuilder) {
        use(AppStateContext)!!
        val room = use(RoomContext)!!
        val nav = useNavigate()
        val qs = questions(room)
        val st = getState(room)
        // Automatically move to next question
        useEffect(q.id, q.state, st.curQuestion?.id) {
            if (q in qs && q.state == QuestionState.RESOLVED && q.id != st.curQuestion?.id && st.curQuestion?.id != null)
                nav(room.urlPrefix + st.curQuestion.urlPrefix)
        }

        // Inject WWTBAM theme for millionaire questions in ladder mode
        val isMilQuestion = q.id in qs.map { it.id }
        if (!isMilQuestion || MillionaireConfig.mode != MillionaireMode.LADDER) return

        cb.apply {
            Global {
                styles {
                    // --- Page background ---
                    "body" {
                        background = "radial-gradient(ellipse at center, #0d0d3b 0%, #050520 100%)".unsafeCast<Background>()
                        color = MilColors.textPrimary
                        minHeight = 100.vh
                    }

                    // --- Room navbar — dark with gold accent ---
                    "nav" {
                        backgroundColor = important(Color("rgba(10, 10, 40, 0.95)"))
                        borderBottom = Border(1.px, LineStyle.solid, Color("rgba(255,215,0,0.3)"))
                        color = MilColors.textPrimary
                        "a, span, div" {
                            color = important(MilColors.textPrimary)
                        }
                        "svg" {
                            color = important(MilColors.textPrimary)
                        }
                    }

                    // --- Main content area — base light text ---
                    // Use !important on div/p to override Emotion's hardcoded
                    // dark colors (#222, #000, rgba(0,0,0,...)).
                    // Do NOT override span/b — those carry highlight colors
                    // (gold, green, red) from our extension code.
                    "main" {
                        color = important(MilColors.textPrimary)
                    }
                    "main div, main p, main h2, main h3" {
                        color = important(MilColors.textPrimary)
                    }
                    "main a" {
                        color = important(MilColors.textSecondary)
                    }

                    // --- Question header — remove gray background ---
                    ".question-header" {
                        background = important(None.none)
                    }
                    ".question-title" {
                        color = important(MilColors.textPrimary)
                    }
                    ".question-status" {
                        color = important(MilColors.textSecondary)
                    }

                    // --- Tabs — hide during game (not needed for live play) ---
                    ".question-tabs" {
                        display = important(None.none)
                    }

                    // --- Prediction box — remove white background ---
                    ".prediction-box" {
                        backgroundColor = important(Color("transparent"))
                    }

                    // --- Prediction description — light text ---
                    ".prediction-description" {
                        color = important(MilColors.textPrimary)
                    }

                    // --- Slider elements ---
                    ".slider-tick" {
                        color = important(Color("rgba(255,255,255,0.5)"))
                    }
                    ".slider-track" {
                        backgroundColor = important(Color("rgba(255,255,255,0.25)"))
                    }
                    ".prediction-slider-box" {
                        color = Color("rgba(255,255,255,0.4)")
                    }
                    // Slider thumb — shift orange (#FF8A00) toward gold (#FFD700)
                    ".slider-thumb-center" {
                        filter = "hue-rotate(24deg) saturate(1.66) brightness(1.33)".unsafeCast<Filter>()
                    }

                    // --- Comment section (if visible) ---
                    "main textarea" {
                        backgroundColor = important(Color("rgba(255,255,255,0.08)"))
                        color = important(MilColors.textPrimary)
                        borderColor = important(Color("rgba(255,255,255,0.2)"))
                    }
                }
            }
        }
    }

    // --- Question Management UI ---

    override fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {
        use(AppStateContext)!!
        val qs = questions(room)
        val presenterCtl = use(PresenterContext)!!
        val st = getState(room)
        val lk = useCoroutineLock()
        val isLadder = MillionaireConfig.mode == MillionaireMode.LADDER
        val ladder = if (isLadder) MillionaireConfig.prizeLadderForCount(qs.size) else emptyList()
        val timerCfg = MillionaireConfig.timerConfig

        // Timer state for management display
        val timerWS = useWebSocket<TimerState?>("/api${room.urlPrefix}/ext/millionaire/timer.ws")
        var timerFraction by useState(1.0)
        val timerState = (timerWS as? WSData)?.data
        useEffectWithCleanup(timerState?.startedAt, timerState?.durationMs, timerState?.running) {
            if (timerState == null || !timerState.running) {
                timerFraction = if (timerState == null) 1.0 else 0.0
                return@useEffectWithCleanup
            }
            fun update() {
                val now = Date.now()
                val elapsed = now - timerState.startedAt
                timerFraction = max(0.0, 1.0 - elapsed / timerState.durationMs)
            }
            update()
            val interval = setInterval(500.milliseconds) { update() }
            onCleanup { clearInterval(interval) }
        }

        if (qs.isEmpty()) return

        fun setState(newState: MillionaireState) {
            lk {
                Client.sendData("/api${room.urlPrefix}/ext/millionaire/state", newState, onError = { showError(it) }) {}
            }
        }

        fun sendTimerAction(action: String) {
            lk {
                Client.sendData("/api${room.urlPrefix}/ext/millionaire/timer", action, onError = { showError(it) }) {}
            }
        }

        cb.apply {
            h2 { +"Millionaire" }

            // Mode display
            div {
                b { +"Mode: " }
                +if (isLadder) "Ladder (additive Brier scoring)" else "Multiplicative"
            }

            // Timer config display
            if (timerCfg !is TimerConfig.None) {
                div {
                    b { +"Timer: " }
                    +when (timerCfg) {
                        is TimerConfig.Fixed -> "Fixed ${timerCfg.seconds}s per question (auto-start)"
                        is TimerConfig.Countdown -> "Countdown ${timerCfg.seconds}s (manual trigger)"
                        TimerConfig.None -> "None"
                    }
                }
            }

            // Validation: missing resolutions
            qs.filter { it.resolution == null }.let { noRes ->
                if (noRes.isNotEmpty()) {
                    b {
                        css { color = NamedColor.red }
                        +"ERROR: The following questions are missing resolution:"
                    }
                    ul {
                        noRes.forEach { li { +it.name } }
                    }
                    return
                }
            }
            // Validation: non-binary
            qs.filter { it.answerSpace !is BinarySpace }.let { nonBin ->
                if (nonBin.isNotEmpty()) {
                    b {
                        css { color = NamedColor.red }
                        +"ERROR: The following questions have non-binary answer spaces:"
                    }
                    ul {
                        nonBin.forEach { li { +it.name } }
                    }
                    return
                }
            }

            // Status display
            div { b { +"Current state: " }; +st.type.toString() }
            div {
                b { +"Current question: " }
                val qNum = st.curIndex + 1
                val qName = st.curQuestion?.name ?: ""
                val prizeStr = if (isLadder && st.curIndex >= 0) {
                    val prize = ladder.getOrElse(st.curIndex) { ladder.lastOrNull() ?: 0 }
                    " (for ${MillionaireConfig.formatPrize(prize)})"
                } else ""
                +"#$qNum$prizeStr — $qName"
            }
            div {
                b { +"Next question: " }
                val nextIdx = st.curIndex + 1
                val nextName = qs.getOrNull(nextIdx)?.name ?: "(none)"
                val nextPrize = if (isLadder && nextIdx < ladder.size)
                    " (for ${MillionaireConfig.formatPrize(ladder[nextIdx])})" else ""
                +"#${nextIdx + 1}$nextPrize — $nextName"
            }

            // Game control buttons
            if (st.type == MillionaireStateType.BEFORE_START)
                Button {
                    +"Start game"
                    onClick = { setState(MillionaireState(MillionaireStateType.ASKING, 0, null)) }
                }
            else if (st.type == MillionaireStateType.ASKING) {
                Button {
                    +"Close question"
                    onClick = { setState(MillionaireState(MillionaireStateType.RESOLVED, st.curIndex, null)) }
                }
            } else if (st.type == MillionaireStateType.RESOLVED) {
                if (st.curIndex < qs.size - 1)
                    Button {
                        +"Next question"
                        onClick = { setState(MillionaireState(MillionaireStateType.ASKING, st.curIndex + 1, null)) }
                    }
            }

            // Timer controls
            if (timerCfg !is TimerConfig.None && st.type == MillionaireStateType.ASKING) {
                div {
                    css { marginTop = 8.px }
                    if (timerState != null && timerState.running) {
                        val remaining = max(0.0, (timerState.durationMs - (Date.now() - timerState.startedAt)) / 1000)
                        b { +"Timer: ${remaining.roundToInt()}s remaining" }
                        +" "
                        Button {
                            +"Cancel timer"
                            onClick = { sendTimerAction("stop") }
                        }
                    } else if (timerCfg is TimerConfig.Countdown) {
                        Button {
                            +"Start countdown (${timerCfg.seconds}s)"
                            onClick = { sendTimerAction("start") }
                        }
                    }
                    if (timerState != null && !timerState.running) {
                        b {
                            css { color = NamedColor.red }
                            +" Timer EXPIRED"
                        }
                    }
                }
            }

            Button {
                +"Open presenter"
                onClick = { presenterCtl.offer(MillionairePV(room.ref)) }
            }
            Button {
                +"Reset to start"
                onClick = { setState(MillionaireState(MillionaireStateType.BEFORE_START, -1, null)) }
            }

            h3 { +"Scoreboard" }
            MillionaireScoreboardPP {
                view = MillionaireScoreboardPV(room.ref)
            }
        }
    }

    // --- Player Prediction Description ---

    override fun myPredictionDescriptionExtra(props: MyPredictionDescriptionProps, cb: ChildrenBuilder) {
        val room = use(RoomContext)!!
        val q = use(QuestionContext)!! ?: return
        val (appState, _) = use(AppStateContext)!!
        val qs = questions(room)
        val qids = qs.map { it.id }
        if (q.id !in qids) return
        if (q.answerSpace !is BinarySpace) return
        val dist = props.dist
        if (dist !is BinaryDistribution) return
        val likelyOutcome = dist.yesProb >= 0.5

        val isLadder = MillionaireConfig.mode == MillionaireMode.LADDER
        val ladder = if (isLadder) MillionaireConfig.prizeLadderForCount(qs.size) else emptyList()

        val resolvedQuestions = qs.takeWhile { it.state == QuestionState.RESOLVED }

        if (isLadder) {
            // Ladder mode: additive scoring
            val scores = resolvedQuestions.mapIndexed { idx, rq ->
                val prizeValue = ladder.getOrElse(idx) { ladder.lastOrNull() ?: 0 }
                computeScoreLadder(appState.myPredictions[rq.ref], rq.resolution!!, prizeValue) ?: 0.0
            }
            val totalBalance = scores.sum()
            val balanceAfterQ = scores.runningFold(0.0) { acc, s -> acc + s }

            cb.apply {
                val qIdx = qids.indexOf(q.id)
                val prizeValue = ladder.getOrElse(qIdx) { ladder.lastOrNull() ?: 0 }

                div {
                    css {
                        fontSize = 18.px
                        fontWeight = FontWeight.bold
                        color = important(MilColors.gold)
                        textShadow = "0 0 10px rgba(255,215,0,0.3)".unsafeCast<TextShadow>()
                    }
                    +"Question ${qIdx + 1} — for ${MillionaireConfig.formatPrize(prizeValue)}"
                }

                if (q.resolved) {
                    val idx = resolvedQuestions.map { it.id }.indexOf(q.id)
                    if (idx >= 0 && idx < scores.size) {
                        val dsc = scores[idx]
                        val word = if (dsc >= 0) "gained" else "lost"
                        val dscColor = if (dsc >= 0) MilColors.scorePositive else MilColors.scoreNegative
                        div {
                            +"You $word "
                            span {
                                css { color = dscColor; fontWeight = FontWeight.bold }
                                +"\$${dsc.absoluteValue.roundToInt()}"
                            }
                            +" on this question."
                        }
                        div {
                            +"Your balance after this question: "
                            val bal = balanceAfterQ[idx + 1]
                            val balColor = if (bal >= 0) MilColors.scorePositive else MilColors.scoreNegative
                            span {
                                css { color = balColor; fontWeight = FontWeight.bold }
                                +"\$${bal.roundToInt()}"
                            }
                        }
                    }
                } else {
                    div {
                        +"Your current balance: "
                        val balColor = if (totalBalance >= 0) MilColors.scorePositive else MilColors.scoreNegative
                        span {
                            css { color = balColor; fontWeight = FontWeight.bold }
                            +"\$${totalBalance.roundToInt()}"
                        }
                    }
                    listOf(likelyOutcome, !likelyOutcome).forEach { ans ->
                        val score = computeScoreLadder(dist.yesProb, ans, prizeValue)
                        val word = if (score >= 0) "gain" else "lose"
                        val sColor = if (score >= 0) MilColors.scorePositive else MilColors.scoreNegative
                        div {
                            +"If the correct answer is "
                            yesNoColored(ans)
                            +", you will "
                            b { +word }
                            +" "
                            span {
                                css { color = sColor; fontWeight = FontWeight.bold }
                                +"\$${score.absoluteValue.roundToInt()}"
                            }
                            val newBal = totalBalance + score
                            val nbColor = if (newBal >= 0) MilColors.scorePositive else MilColors.scoreNegative
                            +" (balance: "
                            span {
                                css { color = nbColor; fontWeight = FontWeight.bold }
                                +"\$${newBal.roundToInt()}"
                            }
                            +")"
                        }
                    }
                }
            }
        } else {
            // Multiplicative mode: original behavior
            val scoreCoef = resolvedQuestions.map { rq ->
                computeScore(appState.myPredictions[rq.ref], rq.resolution!!) ?: 1.0
            }
            val scoreAfterQ = scoreCoef.runningFold(INITIAL_SCORE) { a, b -> a * b }
            val diffScores = scoreAfterQ.zipWithNext { a, b -> b - a }

            cb.apply {
                if (q.resolved) {
                    val idx = resolvedQuestions.map { it.id }.indexOf(q.id)
                    if (idx >= 0 && idx < diffScores.size) {
                        val dsc = diffScores[idx]
                        val word = if (dsc >= 0) "gained" else "lost"
                        div { +"You $word \$ ${dsc.absoluteValue.roundToInt()} on this answer." }
                        div { +"Your score after this answer is \$ ${scoreAfterQ[idx + 1].roundToInt()}." }
                    }
                } else {
                    val myScore = scoreAfterQ.last()
                    div { +"Your current score is $ ${myScore.roundToInt()}" }
                    listOf(likelyOutcome, !likelyOutcome).forEach { ans ->
                        val newScore = myScore * computeScore(dist.yesProb, ans)
                        val diffScore = newScore - myScore
                        val word = if (diffScore >= 0) "gain" else "lose"
                        div {
                            +"If the correct answer is "
                            yesNoColored(ans)
                            +" will "
                            b { +word }
                            +" \$ ${diffScore.absoluteValue.roundToInt()} (ending up with \$ ${newScore.roundToInt()})"
                        }
                    }
                }
            }
        }
    }
}
