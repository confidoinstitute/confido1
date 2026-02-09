package components.redesign.scores

import Client
import components.redesign.HelpIcon
import components.redesign.basic.*
import components.redesign.forms.IconButton
import components.redesign.forms.Select
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.rooms.ScoringHelpDialog
import emotion.css.ClassName
import emotion.react.css
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import normalizedBrier
import payloads.responses.MyScoreItem
import payloads.responses.MyScoresResponse
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.abbr
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tfoot
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.ReactHTML.ul
import react.router.dom.Link
import rooms.Room
import tools.confido.question.Question
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed
import web.cssom.*

enum class ScoreSortMode(val label: String) {
    BEST_FIRST("Best score first"),
    WORST_FIRST("Worst score first"),
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    BY_ROOM("By room"),
}

private fun formatShortDate(instant: Instant): String {
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = ldt.month.name.substring(0, 3).lowercase().replaceFirstChar { it.uppercase() }
    return "${ldt.dayOfMonth} $month ${ldt.year}"
}

/**
 * Maps a normalized Brier score (-3..+1) to a pastel background color
 * on a red-to-green gradient via HSL interpolation.
 */
private fun scoreColor(score: Double): Color {
    // Map score from [-3, +1] to [0, 1]
    val t = ((score + 3.0) / 4.0).coerceIn(0.0, 1.0)
    // Interpolate hue: 0 (red) -> 60 (yellow) -> 120 (green)
    val hue = (t * 120).toInt()
    return Color("hsl($hue, 55%, 88%)")
}

private fun sortItems(items: List<MyScoreItem>, sort: ScoreSortMode): List<MyScoreItem> = when (sort) {
    ScoreSortMode.BEST_FIRST -> items.sortedByDescending { it.score }
    ScoreSortMode.WORST_FIRST -> items.sortedBy { it.score }
    ScoreSortMode.NEWEST_FIRST -> items.sortedByDescending { it.scoreTime }
    ScoreSortMode.OLDEST_FIRST -> items.sortedBy { it.scoreTime }
    ScoreSortMode.BY_ROOM -> items.sortedWith(compareBy({ it.roomName }, { -it.score }))
}

val MyScoresPage = FC<Props> {
    val layoutMode = use(LayoutModeContext)!!
    val isDesktop = layoutMode >= LayoutMode.DESKTOP
    val isPhone = layoutMode == LayoutMode.PHONE

    var helpOpen by useState(false)
    var sortMode by useState(ScoreSortMode.BEST_FIRST)
    var roomFilter by useState<String?>(null) // null = all rooms

    ScoringHelpDialog {
        open = helpOpen
        onClose = { helpOpen = false }
    }

    val response = useSuspendResult {
        Client.httpClient.get("/scores/my").body<MyScoresResponse>()
    }

    PageHeader {
        title = "Your scores"
        allowSidebar = true
        navigateBack = "/"
        rightExtra = IconButton.create {
            HelpIcon {}
            onClick = { helpOpen = true }
        }
    }

        if (response == null) {
            MobileSidePad {
                div {
                    css {
                        fontStyle = FontStyle.italic
                        textAlign = TextAlign.center
                        padding = 20.px
                    }
                    +"Loading..."
                }
            }
        } else if (response.items.isEmpty()) {
            MobileSidePad {
                p { b { +"You have no scored predictions yet." } }
                p {
                    +"In order for your prediction on a question to be scored:"
                    ul {
                        li { +"The question must be a yes-no question." }
                        li { +"The question must have a resolution and be in the Resolved state." }
                        li { +"The question must have a score time configured in its schedule." }
                        li { +"Your prediction must be submitted before the score time." }
                        li { +"Scoring must be enabled for the room." }
                    }
                }
            }
        } else {
            val filteredItems = if (roomFilter != null)
                response.items.filter { it.roomRef.id == roomFilter }
            else
                response.items
            val sortedItems = sortItems(filteredItems, sortMode)
            val filteredTotal = filteredItems.sumOf { it.score }
            val numQuestions = filteredItems.size

            // Achievable score range for this number of questions
            // Best case: all correct with 100% confidence -> score = +1 per question
            // Worst case: all wrong with 100% confidence -> score = -3 per question
            val maxPossible = numQuestions * normalizedBrier(1.0, true)   // +1 per question
            val minPossible = numQuestions * normalizedBrier(1.0, false)  // -3 per question

            // Available rooms for the filter dropdown
            val availableRooms = response.items
                .map { it.roomRef.id to it.roomName }
                .distinct()
                .sortedBy { it.second }

            // Summary card
            Stack {
                css {
                    padding = if (isPhone) 0.px else 15.px
                    paddingTop = 0.px
                    alignItems = AlignItems.stretch
                }
                div {
                    css {
                        backgroundColor = scoreColor(if (numQuestions > 0) filteredTotal / numQuestions else 0.0)
                        borderRadius = 10.px
                        padding = Padding(12.px, 16.px)
                        marginBottom = 12.px
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        gap = 4.px
                    }
                    div {
                        css {
                            display = Display.flex
                            flexDirection = FlexDirection.row
                            alignItems = AlignItems.baseline
                            gap = 8.px
                        }
                        span {
                            css {
                                fontWeight = integer(700)
                                fontSize = 22.px
                            }
                            +"Total: ${filteredTotal.toFixed(1)}"
                        }
                        span {
                            css {
                                color = Color("#666")
                                fontSize = 14.px
                            }
                            val numRooms = filteredItems.map { it.roomRef.id }.distinct().size
                            +"${numQuestions} question${if (numQuestions != 1) "s" else ""}"
                            +" in $numRooms room${if (numRooms != 1) "s" else ""}"
                        }
                    }
                    div {
                        css {
                            color = Color("#888")
                            fontSize = 12.px
                        }
                        +"Range for $numQuestions question${if (numQuestions != 1) "s" else ""}: "
                        +"${minPossible.toFixed(0)} (worst) to +${maxPossible.toFixed(0)} (best)"
                    }
                }

            // Filter + sort controls
                Stack {
                    direction = if (isPhone) FlexDirection.column else FlexDirection.row
                    css {
                        gap = 8.px
                        marginBottom = 12.px
                        alignItems = if (isPhone) AlignItems.stretch else AlignItems.center
                    }
                    Select {
                        value = roomFilter ?: ""
                        onChange = { ev ->
                            val v = ev.target.value
                            roomFilter = v.ifEmpty { null }
                        }
                        css {
                            fontSize = 14.px
                            if (!isPhone) {
                                flexGrow = number(1.0)
                            }
                        }
                        option {
                            value = ""
                            +"All rooms"
                        }
                        availableRooms.forEach { (id, name) ->
                            option {
                                value = id
                                +name
                            }
                        }
                    }
                    Select {
                        value = sortMode.name
                        onChange = { ev ->
                            sortMode = ScoreSortMode.valueOf(ev.target.value)
                        }
                        css {
                            fontSize = 14.px
                            if (!isPhone) {
                                flexGrow = number(1.0)
                            }
                        }
                        ScoreSortMode.entries.forEach { mode ->
                            option {
                                value = mode.name
                                +mode.label
                            }
                        }
                    }
                }

            // Score table
            val isByRoom = sortMode == ScoreSortMode.BY_ROOM
            val showRoomCol = !isPhone && !isByRoom
            val showDateCols = isDesktop

            // Helper for auto-sized columns (minimum width, no stretch)
            fun PropsWithClassName.autoSized() = css { width = 1.px; whiteSpace = WhiteSpace.nowrap }
            fun ChildrenBuilder.autoSizedCol() = ReactHTML.col { autoSized() }

            table {
                css(rowStyleTableCSS) {
                    width = 100.pct
                    if (isPhone) {
                        fontSize = 13.px
                    }
                    // Fix rounded corners for tfoot (baseTableCSS only targets tbody)
                    "tfoot tr:last-child td:first-child" {
                        borderBottomLeftRadius = 10.px
                    }
                    "tfoot tr:last-child td:last-child" {
                        borderBottomRightRadius = 10.px
                    }
                    // When tfoot is present, remove bottom rounding from tbody
                    "tbody:has(+ tfoot) tr:last-child td:first-child" {
                        borderBottomLeftRadius = 0.px
                    }
                    "tbody:has(+ tfoot) tr:last-child td:last-child" {
                        borderBottomRightRadius = 0.px
                    }
                    // Ensure tfoot cells have white background like tbody
                    "tfoot td" {
                        backgroundColor = NamedColor.white
                    }
                }
                // Column sizing: Question and Room stretch, everything else auto-sized
                ReactHTML.colgroup {
                    ReactHTML.col {} // Question (stretches)
                    if (showRoomCol) ReactHTML.col {} // Room (stretches)
                    autoSizedCol() // Pred.
                    autoSizedCol() // Res.
                    autoSizedCol() // Score
                    if (showDateCols) {
                        autoSizedCol() // Scored at
                        autoSizedCol() // Resolved
                    }
                }
                thead {
                    tr {
                        th {
                            +"Question"
                        }
                        if (showRoomCol)
                            th {
                                +"Room"
                            }
                        th {
                            autoSized()
                            abbr {
                                title = "Prediction"
                                css { textDecoration = None.none; borderBottom = Border(1.px, LineStyle.dotted) }
                                +"Pred."
                            }
                        }
                        th {
                            autoSized()
                            abbr {
                                title = "Resolution"
                                css { textDecoration = None.none; borderBottom = Border(1.px, LineStyle.dotted) }
                                +"Res."
                            }
                        }
                        th {
                            autoSized()
                            +"Score"
                        }
                        if (showDateCols) {
                            th {
                                autoSized()
                                abbr {
                                    title = "When the prediction was frozen for scoring"
                                    css { textDecoration = None.none; borderBottom = Border(1.px, LineStyle.dotted) }
                                    +"Scored at"
                                }
                            }
                            th {
                                autoSized()
                                abbr {
                                    title = "When the question was resolved"
                                    css { textDecoration = None.none; borderBottom = Border(1.px, LineStyle.dotted) }
                                    +"Resolved"
                                }
                            }
                        }
                    }
                }
                tbody {
                    if (isByRoom) {
                        // Group by room
                        val grouped = sortedItems.groupBy { it.roomRef.id }
                        val roomOrder = sortedItems.map { it.roomRef.id }.distinct()
                        roomOrder.forEach { roomId ->
                            val roomItems = grouped[roomId] ?: return@forEach
                            val roomName = roomItems.first().roomName
                            val roomTotal = roomItems.sumOf { it.score }
                            val totalCols = 4 + (if (showDateCols) 2 else 0) // Question + Pred + Res + Score + dates
                            // Group header row
                            tr {
                                td {
                                    colSpan = totalCols
                                    css {
                                        "&&" {
                                            backgroundColor = Color("#bbb")
                                            //color = NamedColor.white
                                            fontWeight = integer(600)
                                            padding = Padding(6.px, 8.px)
                                        }
                                    }
                                    Stack {
                                        direction = FlexDirection.row
                                        css {
                                            justifyContent = JustifyContent.spaceBetween
                                            alignItems = AlignItems.center
                                        }
                                        Link {
                                            to = Room.urlPrefix(roomId)
                                            className = LinkSemistyled
                                            +roomName
                                        }
                                        span {
                                            css {
                                                color = NamedColor.black
                                                backgroundColor = scoreColor(roomTotal)
                                                borderRadius = 4.px
                                                padding = Padding(2.px, 6.px)
                                                fontSize = 13.px
                                            }
                                            +roomTotal.toFixed(1)
                                        }
                                    }
                                }
                            }
                            // Question rows
                            roomItems.forEach { item ->
                                ScoreRow {
                                    this.item = item
                                    this.showRoomCol = false // hidden in grouped mode
                                    this.showDateCols = showDateCols
                                }
                            }
                        }
                    } else {
                        sortedItems.forEach { item ->
                            ScoreRow {
                                this.item = item
                                this.showRoomCol = showRoomCol
                                this.showDateCols = showDateCols
                            }
                        }
                    }
                }
                tfoot {
                    tr {
                        val preScoreCols = 1 + (if (showRoomCol) 1 else 0) + 2 // Question + Room? + Pred + Res
                        td {
                            colSpan = preScoreCols
                            css {
                                "&&" {
                                    textAlign = TextAlign.right
                                    fontWeight = integer(700)
                                    paddingRight = 10.px
                                }
                            }
                            +"Total"
                        }
                        td {
                            css {
                                "&&" {
                                    textAlign = TextAlign.right
                                    fontWeight = integer(700)
                                    backgroundColor = scoreColor(filteredTotal)
                                }
                            }
                            +filteredTotal.toFixed(1)
                        }
                        if (showDateCols) {
                            td { colSpan = 2 }
                        }
                    }
                }
            }
            } // Stack wrapper for table
        }
}

private external interface ScoreRowProps : Props {
    var item: MyScoreItem
    var showRoomCol: Boolean
    var showDateCols: Boolean
}

private val ScoreRow = FC<ScoreRowProps> { props ->
    val item = props.item

    tr {
        // Question name (link)
        td {
            css {
                "&&" {
                    maxWidth = 250.px
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                }
            }
            Link {
                to = "${Room.urlPrefix(item.roomRef.id)}${Question.urlPrefix(item.questionRef.id)}"
                className = LinkSemistyled
                +item.questionName
            }
        }

        // Room name (link)
        if (props.showRoomCol) {
            td {
                css {
                    "&&" {
                        maxWidth = 150.px
                        overflow = Overflow.hidden
                        textOverflow = TextOverflow.ellipsis
                        whiteSpace = WhiteSpace.nowrap
                    }
                }
                Link {
                    to = Room.urlPrefix(item.roomRef.id)
                    className = LinkSemistyled
                    +item.roomName
                }
            }
        }

        // Prediction
        td {
            +formatPercent(item.prediction)
        }

        // Resolution
        td {
            css {
                "&&" {
                    fontWeight = integer(600)
                    color = if (item.resolution) Color("#00CC2E") else Color("#FF5555")
                }
            }
            +(if (item.resolution) "Yes" else "No")
        }

        // Score
        td {
            css {
                "&&" {
                    fontWeight = integer(600)
                    backgroundColor = scoreColor(item.score)
                }
            }
            +item.score.toFixed(2)
        }

        // Date columns
        if (props.showDateCols) {
            td {
                css {
                    "&&" {
                        whiteSpace = WhiteSpace.nowrap
                        fontSize = 90.pct
                        color = Color("#666")
                    }
                }
                +formatShortDate(item.scoreTime)
            }
            td {
                css {
                    "&&" {
                        whiteSpace = WhiteSpace.nowrap
                        fontSize = 90.pct
                        color = Color("#666")
                    }
                }
                item.resolveTime?.let { +formatShortDate(it) } ?: +""
            }
        }
    }
}
