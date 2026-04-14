package extensions.millionaire

import components.AppStateContext
import components.presenter.PresenterPageProps
import components.presenter.presenterPageMap

import components.redesign.questions.QuestionContext
import components.redesign.questions.predictions.MyPredictionDescriptionProps
import components.redesign.questions.predictions.yesNoColored
import components.redesign.rooms.RoomContext
import csstype.PropertiesBuilder
import emotion.css.*
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import hooks.useWebSocket
import payloads.responses.WSData
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span

import rooms.Room
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.refs.ref


import tools.confido.spaces.BinarySpace
import tools.confido.spaces.BinaryValue

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
// Active Game Config Hook (reads from server WS endpoint)
// =============================================================================

/**
 * Hook that reads the active [MillionaireGameAction] config from the server
 * via WebSocket. Returns null when no millionaire game is running.
 */
fun useMillionaireConfig(roomId: String): MillionaireGameAction? {
    val ws = useWebSocket<MillionaireGameAction?>("/api${Room.urlPrefix(roomId)}/ext/millionaire/config.ws")
    return (ws as? WSData)?.data
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
                    +formatPrize(prize)
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
// Millionaire Game Action Editor (for presentation editor)
// =============================================================================

private val MillionaireGameActionEditor = FC<tools.confido.extensions.FlowItemEditorProps> { props ->
    val action = props.action as? MillionaireGameAction ?: return@FC
    val allTags = components.redesign.presentation.useRoomTags()

    components.redesign.presentation.FieldLabel { +"Question tag" }
    if (allTags.isNotEmpty()) {
        components.redesign.forms.Select {
            value = action.tag
            onChange = { e -> props.onChange(action.copy(tag = e.target.value)) }
            css { width = 100.pct }
            if (action.tag.isEmpty() || action.tag !in allTags) {
                react.dom.html.ReactHTML.option { value = action.tag; +(if (action.tag.isEmpty()) "Select a tag..." else action.tag) }
            }
            allTags.forEach { tag ->
                react.dom.html.ReactHTML.option { value = tag; +tag }
            }
        }
    } else {
        div {
            css { fontFamily = components.redesign.basic.sansSerif; fontSize = 13.px; color = Color("#999") }
            +"No tags defined on questions in this room."
        }
    }

    components.redesign.presentation.FieldLabel { +"Mode" }
    components.redesign.forms.Select {
        value = action.mode.name
        onChange = { e ->
            MillionaireMode.entries.find { it.name == e.target.value }?.let { props.onChange(action.copy(mode = it)) }
        }
        css { width = 100.pct }
        react.dom.html.ReactHTML.option { value = MillionaireMode.LADDER.name; +"Ladder (additive Brier scoring)" }
        react.dom.html.ReactHTML.option { value = MillionaireMode.MULTIPLICATIVE.name; +"Multiplicative" }
    }

    components.redesign.presentation.FieldLabel { +"Timer" }
    components.redesign.forms.Select {
        val timerKey = when (action.timer) {
            is TimerConfig.None -> "none"
            is TimerConfig.Fixed -> "fixed"
            is TimerConfig.Countdown -> "countdown"
        }
        value = timerKey
        onChange = { e ->
            val newTimer = when (e.target.value) {
                "fixed" -> TimerConfig.Fixed(30)
                "countdown" -> TimerConfig.Countdown(60)
                else -> TimerConfig.None
            }
            props.onChange(action.copy(timer = newTimer))
        }
        css { width = 100.pct }
        react.dom.html.ReactHTML.option { value = "none"; +"None" }
        react.dom.html.ReactHTML.option { value = "fixed"; +"Fixed (auto-start per question)" }
        react.dom.html.ReactHTML.option { value = "countdown"; +"Countdown (manual trigger)" }
    }

    // Timer seconds input (if timer is configured)
    val timerSeconds = when (val t = action.timer) {
        is TimerConfig.Fixed -> t.seconds
        is TimerConfig.Countdown -> t.seconds
        else -> null
    }
    if (timerSeconds != null) {
        components.redesign.presentation.FieldLabel { +"Timer duration (seconds)" }
        react.dom.html.ReactHTML.input {
            asDynamic().type = "number"
            value = timerSeconds.toString()
            min = 5.0
            max = 600.0
            css { width = 80.px; fontSize = 13.px; padding = Padding(2.px, 4.px) }
            onChange = { e ->
                val s = (e.target.asDynamic().value as? String)?.toIntOrNull()
                if (s != null) {
                    val newTimer = when (action.timer) {
                        is TimerConfig.Fixed -> TimerConfig.Fixed(s)
                        is TimerConfig.Countdown -> TimerConfig.Countdown(s)
                        else -> null
                    }
                    if (newTimer != null) props.onChange(action.copy(timer = newTimer))
                }
            }
        }
    }

    // Scoreboard toggle
    div {
        css { display = Display.flex; alignItems = AlignItems.center; gap = 8.px; marginTop = 8.px }
        react.dom.html.ReactHTML.input {
            asDynamic().type = "checkbox"
            checked = action.showScoreboardAfterEach
            onChange = { e ->
                props.onChange(action.copy(showScoreboardAfterEach = e.target.checked))
            }
        }
        span {
            css { fontFamily = components.redesign.basic.sansSerif; fontSize = 14.px }
            +"Show scoreboard after each question"
        }
    }
}

// =============================================================================
// Ladder Question Presenter Page (WWTBAM themed)
// =============================================================================

val MillionaireLadderQuestionPP = FC<PresenterPageProps<MillionaireLadderQuestionPV>> { props ->
    val room = props.view.room.deref() ?: return@FC
    val config = useMillionaireConfig(room.id) ?: return@FC
    val st = MillionaireCE.getState(room, config.tag)
    val question = st.curQuestion ?: return@FC
    val qs = MillionaireCE.questions(room, config.tag)
    val ladder = config.prizeLadderForCount(qs.size)
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
                    +formatPrize(currentPrize)
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
                if (config.timer !is TimerConfig.None) {
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
    val config = useMillionaireConfig(room.id)
    val scoreboard = useWebSocket<Map<Ref<User>, Double>>("/api${Room.urlPrefix(room.id)}/ext/millionaire/scoreboard.ws")
    if (config == null) return@FC
    if (scoreboard !is WSData) return@FC

    val isLadder = config.mode == MillionaireMode.LADDER

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
                            +"$sign€${entry.value.absoluteValue.roundToInt()}"
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
                            +"€ ${it.value.roundToInt()}"
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Client Extension Object
// =============================================================================

object MillionaireCE : ClientExtension, MillionaireExt() {
    override fun registerPresenterPages() = mapOf(
        presenterPageMap(MillionaireScoreboardPP),
        presenterPageMap(MillionaireLadderQuestionPP),
    )

    // --- Flow item type registration ---

    override fun registerFlowItemTypes() = listOf(
        tools.confido.extensions.FlowItemTypeEntry(
            key = "millionaireGame",
            label = "Millionaire game",
            category = tools.confido.extensions.FlowItemCategory.EXPANDABLE,
            createDefault = { MillionaireGameAction() },
            editor = MillionaireGameActionEditor,
            matches = { it is MillionaireGameAction },
        ),
    )

    // --- Hide default prediction description for mil questions in ladder mode ---

    override fun hideDefaultPredictionDesc(dist: ProbabilityDistribution?, question: Question?, b: Boolean): Boolean {
        val room = use(RoomContext)
        val config = useMillionaireConfig(room?.id ?: "")
        if (question == null || room == null || config == null) return false
        return config.mode == MillionaireMode.LADDER && config.tag in question.tags
    }

    // --- Question page theming and extra content ---

    override fun questionPageExtra(q: Question, place: ClientExtension.QuestionPagePlace, cb: ChildrenBuilder) {
        use(AppStateContext)!!
        val room = use(RoomContext)!!
        val config = useMillionaireConfig(room.id)

        // All hooks have been called above -- safe to early-return now
        if (config == null) return

        val qs = questions(room, config.tag)

        // Inject WWTBAM theme for millionaire questions in ladder mode
        val isMilQuestion = q.id in qs.map { it.id }
        if (!isMilQuestion || config.mode != MillionaireMode.LADDER) return

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

            // Show countdown timer on participant's question page
            if (config.timer !is TimerConfig.None && q.state == QuestionState.OPEN) {
                div {
                    css {
                        position = Position.sticky
                        bottom = 0.px
                        zIndex = integer(10)
                        padding = Padding(8.px, 15.px, 10.px)
                        background = "linear-gradient(to bottom, rgba(5,5,32,0), rgba(5,5,32,0.95) 30%)".unsafeCast<Background>()
                    }
                    MillionaireTimerBar {
                        roomId = room.id
                    }
                }
            }
        }
    }

    // --- Player Prediction Description ---

    override fun myPredictionDescriptionExtra(props: MyPredictionDescriptionProps, cb: ChildrenBuilder) {
        val room = use(RoomContext)!!
        val q = use(QuestionContext)!! ?: return
        val (appState, _) = use(AppStateContext)!!
        val config = useMillionaireConfig(room.id) ?: return
        val qs = questions(room, config.tag)
        val qids = qs.map { it.id }
        if (q.id !in qids) return
        if (q.answerSpace !is BinarySpace) return
        val dist = props.dist
        if (dist !is BinaryDistribution) return
        val likelyOutcome = dist.yesProb >= 0.5

        val isLadder = config.mode == MillionaireMode.LADDER
        val ladder = if (isLadder) config.prizeLadderForCount(qs.size) else emptyList()

        val resolvedQuestions = qs.takeWhile { it.state == QuestionState.RESOLVED }

        if (isLadder) {
            // Ladder mode: two-tile layout showing gain/loss for each outcome
            val scores = resolvedQuestions.mapIndexed { idx, rq ->
                val prizeValue = ladder.getOrElse(idx) { ladder.lastOrNull() ?: 0 }
                computeScoreLadder(appState.myPredictions[rq.ref], rq.resolution!!, prizeValue) ?: 0.0
            }

            cb.apply {
                val qIdx = qids.indexOf(q.id)
                val prizeValue = ladder.getOrElse(qIdx) { ladder.lastOrNull() ?: 0 }
                val resolved = q.state == QuestionState.RESOLVED
                val resolution = (q.resolution as? BinaryValue)?.value  // true=Yes, false=No, null=unresolved

                // Compute scores for both outcomes
                val scoreIfNo = computeScoreLadder(dist.yesProb, false, prizeValue)
                val scoreIfYes = computeScoreLadder(dist.yesProb, true, prizeValue)

                // Actual score if resolved
                val actualScore = if (resolved && resolution != null)
                    (if (resolution) scoreIfYes else scoreIfNo) else null

                // Prize value header
                div {
                    css {
                        fontSize = 18.px
                        fontWeight = FontWeight.bold
                        color = important(MilColors.gold)
                        textShadow = "0 0 10px rgba(255,215,0,0.3)".unsafeCast<TextShadow>()
                        textAlign = TextAlign.center
                    }
                    +"Question ${qIdx + 1} — for ${formatPrize(prizeValue)}"
                }

                // Two-tile row: No (left) | Yes (right)
                div {
                    css {
                        display = Display.flex
                        flexDirection = FlexDirection.row
                        gap = 12.px
                        marginTop = 8.px
                    }

                    // Render tile for each outcome: false=No (left), true=Yes (right)
                    for (outcome in listOf(false, true)) {
                        val score = if (outcome) scoreIfYes else scoreIfNo
                        val isCorrect = resolved && resolution == outcome
                        val isWrong = resolved && resolution != outcome
                        val sign = if (score >= 0) "+" else "-"
                        val scoreColor = if (score >= 0) MilColors.scorePositive else MilColors.scoreNegative

                        div {
                            css {
                                flex = Flex(number(1.0), number(1.0), 0.pct)
                                backgroundColor = Color("rgba(255,255,255,0.05)")
                                borderRadius = 10.px
                                padding = Padding(14.px, 16.px)
                                textAlign = TextAlign.center
                                display = Display.flex
                                flexDirection = FlexDirection.column
                                alignItems = AlignItems.center
                                gap = 4.px

                                if (isCorrect) {
                                    // Gold outline for the correct outcome
                                    border = Border(2.px, LineStyle.solid, MilColors.gold)
                                    boxShadow = "0 0 12px rgba(255,215,0,0.25)".unsafeCast<BoxShadow>()
                                } else if (isWrong) {
                                    // Dimmed for the incorrect outcome
                                    opacity = number(0.3)
                                    border = Border(1.px, LineStyle.solid, Color("rgba(255,255,255,0.1)"))
                                } else {
                                    // Unresolved — subtle border
                                    border = Border(1.px, LineStyle.solid, Color("rgba(255,255,255,0.1)"))
                                }
                            }

                            // Label
                            div {
                                css {
                                    fontSize = 13.px
                                    color = important(MilColors.textSecondary)
                                    fontWeight = FontWeight.normal
                                }
                                if (isCorrect) {
                                    val word = if (score >= 0) "You gained" else "You lost"
                                    +word
                                } else {
                                    +(if (outcome) "If Yes" else "If No")
                                }
                            }

                            // Amount
                            div {
                                css {
                                    fontSize = 30.px
                                    fontWeight = FontWeight.bold
                                    fontFamily = string("monospace")
                                    color = important(scoreColor)
                                    lineHeight = number(1.2)
                                }
                                +"$sign€${score.absoluteValue.roundToInt()}"
                            }
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
                if (q.state == QuestionState.RESOLVED) {
                    val idx = resolvedQuestions.map { it.id }.indexOf(q.id)
                    if (idx >= 0 && idx < diffScores.size) {
                        val dsc = diffScores[idx]
                        val word = if (dsc >= 0) "gained" else "lost"
                        div { +"You $word € ${dsc.absoluteValue.roundToInt()} on this answer." }
                        div { +"Your score after this answer is € ${scoreAfterQ[idx + 1].roundToInt()}." }
                    }
                } else {
                    val myScore = scoreAfterQ.last()
                    div { +"Your current score is € ${myScore.roundToInt()}" }
                    listOf(likelyOutcome, !likelyOutcome).forEach { ans ->
                        val newScore = myScore * computeScore(dist.yesProb, ans)
                        val diffScore = newScore - myScore
                        val word = if (diffScore >= 0) "gain" else "lose"
                        div {
                            +"If the correct answer is "
                            yesNoColored(ans)
                            +" will "
                            b { +word }
                            +" € ${diffScore.absoluteValue.roundToInt()} (ending up with € ${newScore.roundToInt()})"
                        }
                    }
                }
            }
        }
    }
}
