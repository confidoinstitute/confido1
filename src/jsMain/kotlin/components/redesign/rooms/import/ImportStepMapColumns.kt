package components.redesign.rooms.import

import components.redesign.basic.*
import components.redesign.forms.*
import payloads.requests.ColumnMapping
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import web.cssom.*
import emotion.react.css
import web.html.HTMLSelectElement

external interface ImportStepMapColumnsProps : Props {
    var state: ImportWizardState
    var onStateChange: (ImportWizardState) -> Unit
    var onBack: () -> Unit
    var onContinue: () -> Unit
}

val ImportStepMapColumns = FC<ImportStepMapColumnsProps> { props ->
    val state = props.state
    val parseResponse = state.parseResponse ?: return@FC
    val allRows = parseResponse.rows
    if (allRows.isEmpty()) return@FC

    val headerRow = if (state.hasHeader) allRows.firstOrNull() else null
    val dataRows = if (state.hasHeader) allRows.drop(1) else allRows
    val previewRows = dataRows.take(5)
    val numColumns = allRows.firstOrNull()?.size ?: 0
    val allowed = allowedMappings(state.questionType)

    // Validation
    val hasTitleMapping = state.columnMappings.contains(ColumnMapping.TITLE)
    val needsMinMax = state.questionType == QuestionType.NUMERIC || state.questionType == QuestionType.DATE
    val hasMin = state.columnMappings.contains(ColumnMapping.MIN)
    val hasMax = state.columnMappings.contains(ColumnMapping.MAX)
    val minMaxValid = !needsMinMax || (hasMin && hasMax)
    val canContinue = hasTitleMapping && minMaxValid && dataRows.isNotEmpty()

    // Question type & options at the top in LayoutWidthWrapper
    LayoutWidthWrapper {
        css {
            gap = 20.px
            padding = Padding(0.px, 20.px)
        }

        // "First row is header" checkbox
        FormSection {
            Stack {
                direction = FlexDirection.row
                css {
                    alignItems = AlignItems.center
                    gap = 10.px
                }
                Checkbox {
                    checked = state.hasHeader
                    onChange = { event ->
                        val isHeader = event.target.checked
                        // Re-compute column mappings when toggling header
                        val newMappings = if (isHeader && parseResponse.suggestedMappings != null) {
                            parseResponse.suggestedMappings
                        } else {
                            List(numColumns) { ColumnMapping.SKIP }
                        }
                        props.onStateChange(state.copy(hasHeader = isHeader, columnMappings = newMappings))
                    }
                }
                div {
                    css {
                        fontFamily = sansSerif
                        fontSize = 15.px
                    }
                    +"First row is a header"
                }
            }
        }

        // Question type selector
        FormSection {
            title = "Question type"
            FormField {
                title = "All imported questions will be"
                OptionGroup<QuestionType>()() {
                    value = state.questionType
                    options = QuestionType.entries.map { it to it.label }
                    onChange = { newType: QuestionType ->
                        // Filter out invalid mappings when changing type
                        val newAllowed = allowedMappings(newType)
                        val newMappings = state.columnMappings.map {
                            if (it in newAllowed) it else ColumnMapping.SKIP
                        }
                        props.onStateChange(state.copy(questionType = newType, columnMappings = newMappings))
                    }
                }
            }

            // Default fields for Numeric type
            if (state.questionType == QuestionType.NUMERIC) {
                Stack {
                    direction = FlexDirection.row
                    css { gap = 15.px }
                    FormField {
                        title = "Default unit"
                        comment = "Used when no Unit column is mapped"
                        TextInput {
                            placeholder = "e.g. km, MWh"
                            value = state.defaultUnit
                            onChange = { props.onStateChange(state.copy(defaultUnit = it.target.value)) }
                        }
                    }
                    FormField {
                        title = "Default decimal places"
                        comment = "Used when no Decimals column is mapped"
                        TextInput {
                            value = state.defaultDecimals.toString()
                            onChange = {
                                val v = it.target.value.toIntOrNull() ?: 1
                                props.onStateChange(state.copy(defaultDecimals = v))
                            }
                        }
                    }
                }
            }
        }
    }

    // Preview table (full width)
    div {
        css {
            padding = Padding(10.px, 15.px)
            overflowX = Auto.auto
        }

        // Validation messages
        if (!hasTitleMapping) {
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 14.px
                    color = Color("#F35454")
                    paddingBottom = 10.px
                    paddingLeft = 5.px
                }
                +"Please map at least one column to \"Question title\"."
            }
        }
        if (needsMinMax && !(hasMin && hasMax)) {
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 14.px
                    color = Color("#F35454")
                    paddingBottom = 10.px
                    paddingLeft = 5.px
                }
                +"${state.questionType.label} questions require both Min and Max columns to be mapped."
            }
        }

        table {
            css(rowStyleTableCSS) {
                width = 100.pct
                "td" {
                    textAlign = TextAlign.left
                    maxWidth = 250.px
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                }
            }

            thead {
                // Column mapping dropdowns row
                tr {
                    for (colIdx in 0 until numColumns) {
                        th {
                            css { padding = 3.px }
                            Select {
                                css {
                                    width = 100.pct
                                    fontSize = 13.px
                                    padding = Padding(6.px, 4.px)
                                    paddingLeft = 16.px
                                }
                                value = state.columnMappings.getOrElse(colIdx) { ColumnMapping.SKIP }.name
                                onChange = { event ->
                                    val target = event.target.unsafeCast<HTMLSelectElement>()
                                    val newMapping = ColumnMapping.valueOf(target.value)
                                    val newMappings = state.columnMappings.toMutableList()
                                    // Ensure uniqueness: if this mapping is already used elsewhere, clear the old one
                                    if (newMapping != ColumnMapping.SKIP) {
                                        for (i in newMappings.indices) {
                                            if (newMappings[i] == newMapping && i != colIdx) {
                                                newMappings[i] = ColumnMapping.SKIP
                                            }
                                        }
                                    }
                                    while (newMappings.size <= colIdx) newMappings.add(ColumnMapping.SKIP)
                                    newMappings[colIdx] = newMapping
                                    props.onStateChange(state.copy(columnMappings = newMappings))
                                }
                                for (mapping in ColumnMapping.entries) {
                                    if (mapping in allowed || mapping == ColumnMapping.SKIP) {
                                        option {
                                            value = mapping.name
                                            +mapping.displayLabel
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Original header row (if present)
                if (headerRow != null) {
                    tr {
                        for (cell in headerRow) {
                            th {
                                +cell
                            }
                        }
                    }
                }
            }
            tbody {
                if (previewRows.isEmpty()) {
                    tr {
                        td {
                            colSpan = numColumns
                            css {
                                textAlign = TextAlign.center
                                color = Color("#999")
                                fontStyle = FontStyle.italic
                            }
                            +"No data rows found"
                        }
                    }
                }
                previewRows.forEachIndexed { rowIdx, row ->
                    tr {
                        for (colIdx in 0 until numColumns) {
                            td {
                                +(row.getOrElse(colIdx) { "" })
                            }
                        }
                    }
                }
                if (dataRows.size > 5) {
                    tr {
                        td {
                            colSpan = numColumns
                            css {
                                textAlign = TextAlign.center
                                color = Color("#999")
                                fontStyle = FontStyle.italic
                            }
                            +"... and ${dataRows.size - 5} more row${if (dataRows.size - 5 > 1) "s" else ""}"
                        }
                    }
                }
            }
        }
    }

    // Navigation buttons
    LayoutWidthWrapper {
        css {
            padding = Padding(0.px, 20.px, 30.px, 20.px)
        }
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.spaceBetween
                gap = 15.px
            }
            Button {
                css {
                    backgroundColor = Color("#E0E0E0")
                    color = Color("#333")
                }
                +"Back"
                onClick = { props.onBack() }
            }
            Button {
                +"Continue"
                disabled = !canContinue
                onClick = { props.onContinue() }
            }
        }
    }
}
