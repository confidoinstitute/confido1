package components.redesign.rooms.import

import Client
import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.*
import io.ktor.client.call.*
import kotlinx.datetime.LocalDate
import payloads.requests.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.router.useNavigate
import rooms.Room
import tools.confido.question.Question
import tools.confido.spaces.*
import web.cssom.*
import emotion.react.css
import hooks.useCoroutineLock

enum class MatchType {
    NEW,       // no match found, will be created
    UPDATE,    // match found, fields differ
    UNCHANGED, // match found, nothing to change
    ERROR,     // validation error
}

/** Result of parsing a single row according to the column mappings. */
data class ParsedQuestion(
    val rowIndex: Int,
    val name: String,
    val description: String,
    val answerSpace: Space?,
    val resolution: Value?,
    val externalId: String?,
    val errors: List<String>,
    val matchType: MatchType,
    /** The set of column mappings that were actually populated from the CSV for this row. */
    val mappedFields: Set<ColumnMapping>,
) {
    val isValid get() = matchType != MatchType.ERROR
    val isActionable get() = matchType == MatchType.NEW || matchType == MatchType.UPDATE
}

/** Parse a date string, supporting ISO 8601, year-month, and year-only formats. */
private fun parseDate(s: String): LocalDate? {
    val trimmed = s.trim()
    if (trimmed.isBlank()) return null
    return try {
        LocalDate.parse(trimmed)
    } catch (e: Exception) {
        try {
            if (trimmed.matches(Regex("\\d{4}-\\d{2}"))) {
                LocalDate.parse("$trimmed-01")
            } else if (trimmed.matches(Regex("\\d{4}"))) {
                LocalDate.parse("$trimmed-01-01")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Parse all data rows according to column mappings, question type, and match against existing questions. */
private fun parseRows(
    state: ImportWizardState,
    existingQuestions: List<Question>,
): List<ParsedQuestion> {
    val parseResponse = state.parseResponse ?: return emptyList()
    val allRows = parseResponse.rows
    val dataRows = if (state.hasHeader) allRows.drop(1) else allRows
    val mappings = state.columnMappings

    // Build lookup maps for matching
    val hasExternalIdMapping = mappings.contains(ColumnMapping.EXTERNAL_ID)
    val byExternalId: Map<String, Question> = existingQuestions
        .filter { it.externalId != null }
        .associateBy { it.externalId!! }
    val byNormalizedName: Map<String, Question> = existingQuestions
        .associateBy { normalizeForMatching(it.name) }

    fun getCol(row: List<String>, mapping: ColumnMapping): String? {
        val idx = mappings.indexOf(mapping)
        if (idx < 0) return null
        return row.getOrNull(idx)?.trim()?.takeIf { it.isNotEmpty() }
    }

    return dataRows.mapIndexed { rowIdx, row ->
        val errors = mutableListOf<String>()
        val name = getCol(row, ColumnMapping.TITLE) ?: ""
        if (name.isBlank()) errors.add("Missing question title")

        val description = getCol(row, ColumnMapping.DESCRIPTION) ?: ""
        val externalId = getCol(row, ColumnMapping.EXTERNAL_ID)

        // Track which fields were actually mapped
        val mapped = mutableSetOf<ColumnMapping>()
        mapped.add(ColumnMapping.TITLE) // always mapped
        if (mappings.contains(ColumnMapping.DESCRIPTION)) mapped.add(ColumnMapping.DESCRIPTION)
        if (mappings.contains(ColumnMapping.RESOLUTION)) mapped.add(ColumnMapping.RESOLUTION)
        if (mappings.contains(ColumnMapping.MIN)) mapped.add(ColumnMapping.MIN)
        if (mappings.contains(ColumnMapping.MAX)) mapped.add(ColumnMapping.MAX)
        if (mappings.contains(ColumnMapping.UNIT)) mapped.add(ColumnMapping.UNIT)
        if (mappings.contains(ColumnMapping.DECIMALS)) mapped.add(ColumnMapping.DECIMALS)
        if (mappings.contains(ColumnMapping.EXTERNAL_ID)) mapped.add(ColumnMapping.EXTERNAL_ID)

        // Build answer space
        val answerSpace: Space? = when (state.questionType) {
            QuestionType.BINARY -> BinarySpace
            QuestionType.NUMERIC -> {
                val minStr = getCol(row, ColumnMapping.MIN)
                val maxStr = getCol(row, ColumnMapping.MAX)
                val unitStr = getCol(row, ColumnMapping.UNIT) ?: state.defaultUnit
                val decimalsStr = getCol(row, ColumnMapping.DECIMALS)
                val decimals = decimalsStr?.toIntOrNull() ?: state.defaultDecimals

                val min = minStr?.toDoubleOrNull()
                val max = maxStr?.toDoubleOrNull()
                if (min == null) { errors.add("Invalid or missing min value"); null }
                else if (max == null) { errors.add("Invalid or missing max value"); null }
                else if (min >= max) { errors.add("Min ($min) must be less than max ($max)"); null }
                else NumericSpace(min = min, max = max, unit = unitStr, decimals = decimals)
            }
            QuestionType.DATE -> {
                val minStr = getCol(row, ColumnMapping.MIN)
                val maxStr = getCol(row, ColumnMapping.MAX)
                val minDate = minStr?.let { parseDate(it) }
                val maxDate = maxStr?.let { parseDate(it) }
                if (minDate == null) { errors.add("Invalid or missing min date"); null }
                else if (maxDate == null) { errors.add("Invalid or missing max date"); null }
                else if (minDate >= maxDate) { errors.add("Min date must be before max date"); null }
                else NumericSpace.fromDates(minDate, maxDate)
            }
        }

        // Parse resolution if mapped
        val resolutionStr = getCol(row, ColumnMapping.RESOLUTION)
        val resolution: Value? = if (resolutionStr != null && answerSpace != null) {
            when (answerSpace) {
                is BinarySpace -> {
                    when (resolutionStr.lowercase()) {
                        "yes", "true", "1" -> BinaryValue(true)
                        "no", "false", "0" -> BinaryValue(false)
                        else -> { errors.add("Cannot parse resolution: '$resolutionStr'"); null }
                    }
                }
                is NumericSpace -> {
                    if (answerSpace.representsDays) {
                        val date = parseDate(resolutionStr)
                        if (date != null) {
                            val epochSec = date.toEpochDays() * 86400.0
                            NumericValue(answerSpace, epochSec)
                        } else {
                            errors.add("Cannot parse date resolution: '$resolutionStr'"); null
                        }
                    } else {
                        val num = resolutionStr.toDoubleOrNull()
                        if (num != null) {
                            NumericValue(answerSpace, num)
                        } else {
                            errors.add("Cannot parse numeric resolution: '$resolutionStr'"); null
                        }
                    }
                }
            }
        } else null

        // Determine match type
        val matchType = if (errors.isNotEmpty() || name.isBlank() || answerSpace == null) {
            MatchType.ERROR
        } else {
            // Find matching existing question
            val match = if (externalId != null && hasExternalIdMapping) {
                byExternalId[externalId]
            } else {
                byNormalizedName[normalizeForMatching(name)]
            }

            if (match == null) {
                MatchType.NEW
            } else {
                // Check if anything would actually change
                val nameChanged = match.name != name
                val descChanged = ColumnMapping.DESCRIPTION in mapped && match.description != description
                val spaceChanged = match.answerSpace != answerSpace
                val resChanged = ColumnMapping.RESOLUTION in mapped && match.resolution != resolution
                val extIdChanged = externalId != null && match.externalId != externalId

                if (nameChanged || descChanged || spaceChanged || resChanged || extIdChanged) {
                    MatchType.UPDATE
                } else {
                    MatchType.UNCHANGED
                }
            }
        }

        ParsedQuestion(
            rowIndex = rowIdx,
            name = name,
            description = description,
            answerSpace = answerSpace,
            resolution = resolution,
            externalId = externalId,
            errors = errors,
            matchType = matchType,
            mappedFields = mapped,
        )
    }
}

/** Format answer space for display in the review table. */
private fun formatAnswerSpace(space: Space?): String = when (space) {
    null -> "-"
    is BinarySpace -> "Yes / No"
    is NumericSpace -> if (space.representsDays) {
        val minDate = LocalDate.fromEpochDays((space.min / 86400).toInt())
        val maxDate = LocalDate.fromEpochDays((space.max / 86400).toInt())
        "Date [$minDate, $maxDate]"
    } else {
        val range = "${space.min}\u2013${space.max}"
        if (space.unit.isNotEmpty()) "$range ${space.unit}" else range
    }
}

external interface ImportStepReviewProps : Props {
    var room: Room
    var state: ImportWizardState
    var onStateChange: (ImportWizardState) -> Unit
    var onBack: () -> Unit
}

val ImportStepReview = FC<ImportStepReviewProps> { props ->
    val state = props.state
    val (appState, _) = use(AppStateContext)!!
    val navigate = useNavigate()
    val submitLock = useCoroutineLock()
    var importError by useState<String?>(null)
    var optionsDialogOpen by useState(false)

    // Get existing questions in this room from client-side state
    val existingQuestions = useMemo(appState, props.room) {
        props.room.questions.mapNotNull { qRef -> appState.questions[qRef.id] }
    }

    val parsedQuestions = useMemo(state, existingQuestions) { parseRows(state, existingQuestions) }
    val newQuestions = parsedQuestions.filter { it.matchType == MatchType.NEW }
    val updateQuestions = parsedQuestions.filter { it.matchType == MatchType.UPDATE }
    val unchangedQuestions = parsedQuestions.filter { it.matchType == MatchType.UNCHANGED }
    val errorQuestions = parsedQuestions.filter { it.matchType == MatchType.ERROR }
    val actionableQuestions = parsedQuestions.filter { it.isActionable }

    // Options dialog
    ImportOptionsDialog {
        open = optionsDialogOpen
        onClose = { optionsDialogOpen = false }
        options = state.options
        onOptionsChange = { newOpts ->
            props.onStateChange(state.copy(options = newOpts))
        }
    }

    // Summary banner
    LayoutWidthWrapper {
        css {
            padding = Padding(10.px, 20.px)
        }
        div {
            css {
                fontFamily = sansSerif
                fontSize = 15.px
                padding = Padding(12.px, 16.px)
                borderRadius = 6.px
                backgroundColor = if (errorQuestions.isEmpty()) Color("#E8F5E9") else Color("#FFF8E1")
                border = Border(1.px, LineStyle.solid,
                    if (errorQuestions.isEmpty()) Color("#A5D6A7") else Color("#FFE082"))
                lineHeight = 22.px
            }
            val parts = mutableListOf<String>()
            if (newQuestions.isNotEmpty()) parts.add("${newQuestions.size} new")
            if (updateQuestions.isNotEmpty()) parts.add("${updateQuestions.size} to update")
            if (unchangedQuestions.isNotEmpty()) parts.add("${unchangedQuestions.size} unchanged")

            if (parts.isNotEmpty()) {
                +parts.joinToString(", ")
            }
            if (errorQuestions.isNotEmpty()) {
                if (parts.isNotEmpty()) +", "
                span {
                    css { color = Color("#F57C00"); fontWeight = integer(600) }
                    +"${errorQuestions.size} with errors"
                }
            }
        }
    }

    // Review table (full width)
    div {
        css {
            padding = Padding(10.px, 15.px)
            overflowX = Auto.auto
        }
        table {
            css(rowStyleTableCSS) {
                width = 100.pct
                "td" {
                    textAlign = TextAlign.left
                    maxWidth = 300.px
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                }
                "th" {
                    textAlign = TextAlign.left
                }
            }
            thead {
                tr {
                    th { css { width = 1.px; whiteSpace = WhiteSpace.nowrap }; +"#" }
                    th { +"Title" }
                    th { +"Description" }
                    if (state.questionType != QuestionType.BINARY) {
                        th { css { whiteSpace = WhiteSpace.nowrap }; +"Answer Space" }
                    }
                    if (state.columnMappings.contains(ColumnMapping.RESOLUTION)) {
                        th { +"Resolution" }
                    }
                    th { css { width = 1.px; whiteSpace = WhiteSpace.nowrap }; +"Status" }
                }
            }
            tbody {
                parsedQuestions.forEach { pq ->
                    tr {
                        key = pq.rowIndex.toString()
                        val rowColor = when (pq.matchType) {
                            MatchType.ERROR -> Color("#FFF8E1")
                            MatchType.UNCHANGED -> Color("#F5F5F5")
                            MatchType.UPDATE -> Color("#E3F2FD")
                            MatchType.NEW -> null
                        }
                        if (rowColor != null) {
                            css {
                                "&& td" {
                                    backgroundColor = rowColor
                                }
                            }
                        }
                        if (pq.matchType == MatchType.UNCHANGED) {
                            css {
                                opacity = number(0.6)
                            }
                        }
                        td { +"${pq.rowIndex + 1}" }
                        td { +pq.name }
                        td {
                            css { color = Color("#666") }
                            +pq.description
                        }
                        if (state.questionType != QuestionType.BINARY) {
                            td {
                                css { fontSize = 13.px }
                                +formatAnswerSpace(pq.answerSpace)
                            }
                        }
                        if (state.columnMappings.contains(ColumnMapping.RESOLUTION)) {
                            td {
                                css { fontSize = 13.px }
                                +(pq.resolution?.format() ?: "-")
                            }
                        }
                        td {
                            css { whiteSpace = WhiteSpace.nowrap }
                            when (pq.matchType) {
                                MatchType.NEW -> {
                                    span {
                                        css { color = Color("#4CAF50"); fontWeight = integer(600); fontSize = 12.px }
                                        +"+ New"
                                    }
                                }
                                MatchType.UPDATE -> {
                                    span {
                                        css { color = Color("#1976D2"); fontWeight = integer(600); fontSize = 12.px }
                                        +"\u270E Update"
                                    }
                                }
                                MatchType.UNCHANGED -> {
                                    span {
                                        css { color = Color("#999"); fontSize = 12.px }
                                        +"\u2013 Same"
                                    }
                                }
                                MatchType.ERROR -> {
                                    span {
                                        title = pq.errors.joinToString("; ")
                                        css { color = Color("#F57C00"); cursor = Cursor.help; fontSize = 12.px }
                                        +"\u26A0 Error"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom action bar
    LayoutWidthWrapper {
        css {
            padding = Padding(10.px, 20.px, 30.px, 20.px)
            gap = 15.px
        }

        // Error display
        importError?.let { err ->
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 14.px
                    color = Color("#F35454")
                    padding = 10.px
                    backgroundColor = Color("#FFF0F0")
                    borderRadius = 5.px
                }
                +err
            }
        }

        // Navigation buttons
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.spaceBetween
                alignItems = AlignItems.center
                gap = 15.px
            }
            Button {
                css {
                    backgroundColor = Color("#E0E0E0")
                    color = Color("#333")
                }
                +"Back"
                disabled = submitLock.running
                onClick = { props.onBack() }
            }

            Stack {
                direction = FlexDirection.row
                css {
                    gap = 10.px
                    alignItems = AlignItems.center
                }
                // Options button (only relevant for new questions)
                if (newQuestions.isNotEmpty()) {
                    Button {
                        css {
                            backgroundColor = Color("#F0F0F0")
                            color = Color("#555")
                        }
                        +"Question options\u2026"
                        onClick = { optionsDialogOpen = true }
                    }
                }

                // Import button
                Button {
                    disabled = actionableQuestions.isEmpty() || submitLock.running
                    onClick = {
                        submitLock {
                            importError = null
                            val importPayload = ImportQuestions(
                                questions = actionableQuestions.map { pq ->
                                    ImportedQuestion(
                                        name = pq.name,
                                        description = pq.description,
                                        answerSpace = pq.answerSpace!!,
                                        resolution = pq.resolution,
                                        externalId = pq.externalId,
                                        mappedFields = pq.mappedFields,
                                    )
                                },
                                options = state.options,
                            )
                            Client.sendData(
                                "${props.room.urlPrefix}/questions/import",
                                importPayload,
                                onError = { importError = it },
                            ) {
                                val result: ImportResult = body()
                                navigate(props.room.urlPrefix)
                            }
                        }
                    }
                    // Button label
                    val parts = mutableListOf<String>()
                    if (newQuestions.isNotEmpty()) parts.add("create ${newQuestions.size}")
                    if (updateQuestions.isNotEmpty()) parts.add("update ${updateQuestions.size}")
                    if (parts.isEmpty()) {
                        +"Nothing to import"
                    } else {
                        +"Import: ${parts.joinToString(", ")}"
                    }
                }
            }
        }
    }
}
