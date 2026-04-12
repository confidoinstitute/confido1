package components.redesign.rooms.import

import Client
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
import tools.confido.question.CommentVisibility
import tools.confido.question.GroupPredictionVisibility
import tools.confido.question.QuestionState
import tools.confido.spaces.*
import web.cssom.*
import emotion.react.css
import hooks.useCoroutineLock

/** Result of parsing a single row according to the column mappings. */
data class ParsedQuestion(
    val rowIndex: Int,
    val name: String,
    val description: String,
    val answerSpace: Space?,
    val resolution: Value?,
    val errors: List<String>,
) {
    val isValid get() = errors.isEmpty() && name.isNotBlank() && answerSpace != null
}

/** Parse a date string, supporting ISO 8601, year-month, and year-only formats. */
private fun parseDate(s: String): LocalDate? {
    val trimmed = s.trim()
    if (trimmed.isBlank()) return null
    return try {
        // Full ISO date: 2025-03-15
        LocalDate.parse(trimmed)
    } catch (e: Exception) {
        try {
            // Year-month: 2025-03
            if (trimmed.matches(Regex("\\d{4}-\\d{2}"))) {
                LocalDate.parse("$trimmed-01")
            } else if (trimmed.matches(Regex("\\d{4}"))) {
                // Year-only: 2025
                LocalDate.parse("$trimmed-01-01")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Parse all data rows according to column mappings and question type. */
private fun parseRows(
    state: ImportWizardState,
): List<ParsedQuestion> {
    val parseResponse = state.parseResponse ?: return emptyList()
    val allRows = parseResponse.rows
    val dataRows = if (state.hasHeader) allRows.drop(1) else allRows
    val mappings = state.columnMappings

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

        ParsedQuestion(
            rowIndex = rowIdx,
            name = name,
            description = description,
            answerSpace = answerSpace,
            resolution = resolution,
            errors = errors,
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
        val range = "${space.min}–${space.max}"
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
    val navigate = useNavigate()
    val submitLock = useCoroutineLock()
    var importError by useState<String?>(null)
    var optionsExpanded by useState(false)

    val parsedQuestions = useMemo(state) { parseRows(state) }
    val validQuestions = parsedQuestions.filter { it.isValid }
    val invalidQuestions = parsedQuestions.filter { !it.isValid }

    val options = state.options
    val gpv = GroupPredictionVisibility.fromBooleans(options.groupPredVisible, options.groupPredRequirePrediction)

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
                backgroundColor = if (invalidQuestions.isEmpty()) Color("#E8F5E9") else Color("#FFF8E1")
                border = Border(1.px, LineStyle.solid,
                    if (invalidQuestions.isEmpty()) Color("#A5D6A7") else Color("#FFE082"))
            }
            if (invalidQuestions.isEmpty()) {
                +"${validQuestions.size} question${if (validQuestions.size != 1) "s" else ""} ready to import"
            } else {
                +"${validQuestions.size} question${if (validQuestions.size != 1) "s" else ""} ready to import, "
                span {
                    css { color = Color("#F57C00"); fontWeight = integer(600) }
                    +"${invalidQuestions.size} with errors (will be skipped)"
                }
            }
        }
    }

    // Review table (full width)
    div {
        css {
            padding = Padding(10.px, 30.px)
            overflowX = Auto.auto
        }
        table {
            css {
                width = 100.pct
                borderCollapse = BorderCollapse.collapse
                fontFamily = sansSerif
                fontSize = 14.px

                "th, td" {
                    border = Border(1.px, LineStyle.solid, Color("#DDD"))
                    padding = Padding(8.px, 10.px)
                    textAlign = TextAlign.left
                }
                "th" {
                    backgroundColor = Color("#F8F8F8")
                    fontWeight = integer(500)
                }
            }
            thead {
                tr {
                    th { css { width = 40.px }; +"#" }
                    th { +"Title" }
                    th { +"Description" }
                    if (state.questionType != QuestionType.BINARY) {
                        th { +"Answer Space" }
                    }
                    if (state.columnMappings.contains(ColumnMapping.RESOLUTION)) {
                        th { +"Resolution" }
                    }
                    th { css { width = 40.px }; +"" } // status column
                }
            }
            tbody {
                parsedQuestions.forEach { pq ->
                    tr {
                        key = pq.rowIndex.toString()
                        css {
                            if (!pq.isValid) {
                                backgroundColor = Color("#FFF8E1")
                            }
                        }
                        td { +"${pq.rowIndex + 1}" }
                        td {
                            css {
                                maxWidth = 300.px
                                overflow = Overflow.hidden
                                textOverflow = TextOverflow.ellipsis
                                whiteSpace = WhiteSpace.nowrap
                            }
                            +pq.name
                        }
                        td {
                            css {
                                maxWidth = 200.px
                                overflow = Overflow.hidden
                                textOverflow = TextOverflow.ellipsis
                                whiteSpace = WhiteSpace.nowrap
                                color = Color("#666")
                            }
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
                            if (pq.isValid) {
                                span {
                                    css { color = Color("#4CAF50") }
                                    +"\u2713" // checkmark
                                }
                            } else {
                                span {
                                    title = pq.errors.joinToString("; ")
                                    css { color = Color("#F57C00"); cursor = Cursor.help }
                                    +"\u26A0" // warning
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Expandable options section
    LayoutWidthWrapper {
        css {
            padding = Padding(10.px, 20.px, 30.px, 20.px)
            gap = 15.px
        }

        // Expander header
        div {
            css {
                fontFamily = sansSerif
                fontSize = 15.px
                fontWeight = integer(500)
                cursor = Cursor.pointer
                padding = Padding(10.px, 0.px)
                color = Color("#6319FF")
                userSelect = None.none
            }
            onClick = { optionsExpanded = !optionsExpanded }
            +(if (optionsExpanded) "\u25BC " else "\u25B6 ")
            +"Question options"
        }

        if (optionsExpanded) {
            FormSection {
                title = "Initial state"
                OptionGroup<QuestionState>()() {
                    value = if (!options.visible) QuestionState.DRAFT
                            else if (options.open) QuestionState.OPEN
                            else QuestionState.CLOSED
                    this.options = listOf(
                        QuestionState.DRAFT to "Draft",
                        QuestionState.OPEN to "Open",
                    )
                    onChange = { newState: QuestionState ->
                        val newOpts = when (newState) {
                            QuestionState.DRAFT -> options.copy(visible = false, open = false)
                            QuestionState.OPEN -> options.copy(visible = true, open = true)
                            else -> options
                        }
                        props.onStateChange(state.copy(options = newOpts))
                    }
                }
            }

            FormSection {
                title = "Anchoring"
                RadioGroup<GroupPredictionVisibility>()() {
                    this.title = "Group answer visible to"
                    value = gpv
                    this.options = listOf(
                        GroupPredictionVisibility.EVERYONE to "all room members",
                        GroupPredictionVisibility.ANSWERED to "those who answered",
                        GroupPredictionVisibility.MODERATOR_ONLY to "moderators only",
                    )
                    onChange = { newGpv: GroupPredictionVisibility ->
                        props.onStateChange(state.copy(options = options.copy(
                            groupPredVisible = newGpv.groupPredVisible,
                            groupPredRequirePrediction = newGpv.groupPredRequirePrediction,
                        )))
                    }
                }
            }

            FormSection {
                title = "Comments"
                FormSwitch {
                    label = "Allow comments"
                    comment = "Whether participants can comment on imported questions"
                    checked = options.allowComments
                    onChange = {
                        props.onStateChange(state.copy(options = options.copy(allowComments = it.target.checked)))
                    }
                }
                if (options.allowComments) {
                    RadioGroup<CommentVisibility>()() {
                        title = "Comments visible to"
                        value = options.commentVisibility
                        this.options = listOf(
                            CommentVisibility.EVERYONE to "all room members",
                            CommentVisibility.ANSWERED to "those who answered",
                            CommentVisibility.MODERATOR_ONLY to "moderators only",
                        )
                        onChange = { cv: CommentVisibility ->
                            props.onStateChange(state.copy(options = options.copy(commentVisibility = cv)))
                        }
                    }
                }
            }
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
                gap = 15.px
                paddingTop = 10.px
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
            Button {
                disabled = validQuestions.isEmpty() || submitLock.running
                onClick = {
                    submitLock {
                        importError = null
                        val importPayload = ImportQuestions(
                            questions = validQuestions.map { pq ->
                                ImportedQuestion(
                                    name = pq.name,
                                    description = pq.description,
                                    answerSpace = pq.answerSpace!!,
                                    resolution = pq.resolution,
                                )
                            },
                            options = state.options,
                        )
                        Client.sendData(
                            "${props.room.urlPrefix}/questions/import",
                            importPayload,
                            onError = { importError = it },
                        ) {
                            val count: String = body()
                            navigate(props.room.urlPrefix)
                        }
                    }
                }
                if (invalidQuestions.isEmpty()) {
                    +"Import ${validQuestions.size} question${if (validQuestions.size != 1) "s" else ""}"
                } else {
                    +"Import ${validQuestions.size} question${if (validQuestions.size != 1) "s" else ""} (${invalidQuestions.size} skipped)"
                }
            }
        }
    }
}
