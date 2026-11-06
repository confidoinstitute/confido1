package tools.confido.web.components.presentation

import tools.confido.question.Question
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.state.CURRENT_QUESTION
import tools.confido.state.QuestionSelection
import web.cssom.*

/**
 * A question-selection control (repeat loops, ask / reveal steps): a select-look
 * button summarizing the selection, opening a checkbox popover. "All room
 * questions" and per-tag rows are *dynamic* checks (they imply their members'
 * checkmarks and keep including questions added later); question rows are static
 * picks. Unchecking an implied question dissolves the covering dynamic checks into
 * static picks (see [QuestionSelection.toggleQuestion]). Plain checkboxes in a
 * popover rather than a react-select: everything here is a toggle, so a combobox
 * bought only fights (decoded clicks, suppressed value rendering, inconsistent
 * selected styling).
 */
external interface QuestionSelectionPickerProps : Props {
    var selection: QuestionSelection
    var roomQuestions: List<Question>
    var onUpdate: ((QuestionSelection) -> QuestionSelection) -> Unit
    /** Popover heading. */
    var header: String
    /** Inside a repeat body: offer the loop's question as an exclusive choice. */
    var currentQuestionOption: Boolean?
}

val QuestionSelectionPicker = FC<QuestionSelectionPickerProps> { props ->
    var open by useState(false)
    var search by useState("")
    val sel = props.selection
    val qs = props.roomQuestions
    val tags = qs.flatMap { it.tags }.distinct().sorted()
    val isCurrent = sel.questions == listOf(CURRENT_QUESTION)
    val n = qs.count { sel.selects(it) }
    val summary = when {
        isCurrent -> "↻ Each question in turn"
        sel.all -> "All room questions ($n)"
        sel.tags.isNotEmpty() && sel.questions.isEmpty() ->
            "Questions tagged ${sel.tags.sorted().joinToString(", ") { "\"$it\"" }} ($n)"
        sel.isEmpty -> "Choose questions…"
        n == 1 -> qs.first { sel.selects(it) }.name
        else -> "$n of ${qs.size} questions selected"
    }

    ButtonUnstyled {
        css {
            width = 100.pct
            display = Display.flex
            justifyContent = JustifyContent.spaceBetween
            alignItems = AlignItems.center
            gap = 8.px
            padding = Padding(8.px, 10.px)
            border = Border(1.px, LineStyle.solid, Color("#cccccc"))
            borderRadius = 4.px
            backgroundColor = NamedColor.white
            fontFamily = sansSerif
            fontSize = 14.px
            textAlign = TextAlign.left
            cursor = Cursor.pointer
        }
        span {
            css {
                overflow = Overflow.hidden; textOverflow = TextOverflow.ellipsis; whiteSpace = WhiteSpace.nowrap
                if (sel.isEmpty) color = Color("#888888")
            }
            +summary
        }
        span { css { color = Color("#888888") }; +"▾" }
        // The popover matches this control's width automatically: MenuPopover
        // never renders narrower than its trigger.
        onClick = { open = true }
    }

    // A row toggles via a single click anywhere on it; the checkbox itself is
    // display-only (its click bubbles to the row) to avoid double toggling.
    fun ChildrenBuilder.checkRow(rowKey: String, checked: Boolean, bold: Boolean,
                                 label: String, onToggle: () -> Unit) {
        Stack {
            key = rowKey
            direction = FlexDirection.row
            css {
                alignItems = AlignItems.center
                gap = 8.px
                padding = Padding(3.px, 15.px)
                cursor = Cursor.pointer
                "&:hover" { backgroundColor = Color("#f5f5f5") }
            }
            Checkbox {
                this.checked = checked
                onChange = {}
                // No <label> wrapper: a label forwards clicks to its input, so the
                // row's onClick would fire twice per checkbox click (= no-op toggle).
                wrapLabel = false
            }
            span {
                css {
                    fontFamily = sansSerif
                    fontSize = 14.px
                    if (bold) fontWeight = integer(600)
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                }
                +label
            }
            onClick = { onToggle() }
        }
    }

    // Picking anything real replaces the loop sentinel (and vice versa) — mixing
    // "the loop's question" with fixed picks has no sensible meaning.
    fun update(f: (QuestionSelection) -> QuestionSelection) = props.onUpdate { cur ->
        f(if (cur.questions == listOf(CURRENT_QUESTION)) QuestionSelection() else cur)
    }

    DialogMenu {
        this.open = open
        onClose = { open = false; search = "" }
        DialogMenuHeader { text = props.header }
        div {
            div {
                css { padding = Padding(0.px, 15.px, 8.px) }
                TextInput {
                    value = search
                    placeholder = "Search questions…"
                    autoFocus = true
                    onChange = { e -> search = e.target.value }
                }
            }
            val filter = search.trim()
            if (filter.isEmpty()) {
                if (props.currentQuestionOption == true) {
                    checkRow("//current", isCurrent, true, "↻ Each question in turn") {
                        props.onUpdate { QuestionSelection(questions = listOf(CURRENT_QUESTION)) }
                    }
                }
                checkRow("//all", sel.all, true, "All room questions (${qs.size})") {
                    update { it.toggleAll() }
                }
                tags.forEach { t ->
                    checkRow("//tag:$t", t in sel.tags, true,
                        "Questions tagged \"$t\" (${qs.count { q -> t in q.tags }})") {
                        update { it.toggleTag(t) }
                    }
                }
                div { css { borderTop = Border(1.px, LineStyle.solid, Color("#eeeeee")); margin = Margin(6.px, 0.px) } }
            }
            div {
                css { maxHeight = 260.px; overflowY = Auto.auto }
                qs.filter { filter.isEmpty() || it.name.contains(filter, ignoreCase = true) }.forEach { q ->
                    checkRow(q.id, !isCurrent && sel.selects(q), false, q.name) {
                        update { it.toggleQuestion(q, qs) }
                    }
                }
            }
            div {
                css {
                    padding = Padding(6.px, 15.px, 0.px)
                    fontFamily = sansSerif
                    fontSize = 11.px
                    color = Color("#999999")
                }
                +"\"All\" and tags also include questions you add later; individually picked questions are fixed."
            }
        }
    }
}
