package tools.confido.web.components.redesign.presentation

import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.forms.ButtonUnstyled
import tools.confido.web.components.redesign.forms.Checkbox
import tools.confido.web.components.redesign.forms.FormSection
import tools.confido.web.components.redesign.forms.TextInput
import tools.confido.web.components.redesign.questions.ChipCSS
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.extensions.FlowItemCategory
import tools.confido.extensions.FlowItemTypeEntry
import tools.confido.state.ActionItem
import tools.confido.state.FlowAction
import tools.confido.state.FlowItem
import tools.confido.state.ForEachItem
import tools.confido.state.NoteItem
import tools.confido.utils.generateId
import web.cssom.*

/**
 * Unified dialog for adding/configuring a flow item. The type is picked from
 * chips grouped by what the step *means* — "show something on the projector"
 * (VIEW category) vs "do something" (side effects, incl. expandable metas) vs a
 * presenter note — even though views and actions share one mechanism underneath.
 * The selected type's editor renders below the chips.
 *
 * Slides are deliberately absent: the slide-order invariant would relocate a
 * manually placed slide anyway, so re-adding deleted slides goes through the
 * toolbar's Auto-populate instead.
 *
 * Wrapped with [dialogStateWrapper] so that all internal state is reset
 * (via React remount) each time the dialog opens.
 */
external interface FlowItemConfigDialogProps : BaseDialogProps {
    /** Non-null when editing an existing action item's action. */
    var action: FlowAction?
    /** True while inserting into a loop body — hides the (non-nestable) loop kind. */
    var inLoopBody: Boolean?
    var onConfirm: ((FlowItem) -> Unit)?
}

/** Pseudo-kinds for non-action item types (not registry [FlowItemTypeEntry]s). */
private const val NOTE_KEY = "//note"
private const val FOREACH_KEY = "//forEach"

val FlowItemConfigDialog = dialogStateWrapper(FC<FlowItemConfigDialogProps> { props ->
    val types = flowItemTypes
    val editing = props.action != null

    // Compute initial state from props.action (runs fresh on each open thanks to
    // dialogStateWrapper). When inserting, nothing is preselected — the user
    // picks a chip first.
    val initEntry = props.action?.let { a -> types.find { it.matches(a) } }
    var selectedKey by useState(initEntry?.key)
    var currentAction by useState(props.action)
    var noteText by useState("")
    var noteWarning by useState(false)

    val selectedEntry = types.find { it.key == selectedKey }
    // Two steps: chips while choosing a type, then just the configuration with a
    // compact "change type" way back (the full chip grid would eat half the dialog).
    val choosing = selectedKey == null

    fun selectEntry(entry: FlowItemTypeEntry) {
        selectedKey = entry.key
        // Re-picking the type currently being configured (or the edited item's
        // own type) restores that configuration instead of resetting to defaults.
        currentAction = currentAction?.takeIf { entry.matches(it) }
            ?: props.action?.takeIf { entry.matches(it) }
            ?: entry.createDefault()
    }

    fun ChildrenBuilder.typeChips(title: String, entries: List<FlowItemTypeEntry>) {
        if (entries.isEmpty()) return
        div {
            FieldLabel { +title }
            Stack {
                direction = FlexDirection.row
                css { flexWrap = FlexWrap.wrap; gap = 6.px; marginTop = 6.px }
                entries.forEach { entry ->
                    typeChip(entry.label, selectedKey == entry.key) { selectEntry(entry) }
                }
            }
        }
    }

    Dialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = if (editing) "Edit item" else "Add item"
        action = "OK"
        disabledAction = when (selectedKey) {
            null -> true
            NOTE_KEY, FOREACH_KEY -> false
            else -> currentAction == null
        }
        onAction = {
            val item: FlowItem? = when {
                selectedKey == NOTE_KEY -> NoteItem(id = generateId(), text = noteText, warning = noteWarning)
                // Starts over everything; narrowed down in the loop dialog (opens next).
                selectedKey == FOREACH_KEY -> ForEachItem(id = generateId(), all = true)
                currentAction != null -> ActionItem(id = generateId(), action = currentAction!!)
                else -> null
            }
            item?.let { props.onConfirm?.invoke(it) }
            props.onClose?.invoke()
        }

        FormSection {
            if (choosing) {
                // BOTH types (e.g. the calibration leaderboard: shows a view AND
                // navigates followers) sort under "show" — displaying something is
                // their defining trait, the side effect is auxiliary.
                typeChips("Show on projector", types.filter {
                    it.category == FlowItemCategory.VIEW || it.category == FlowItemCategory.BOTH
                })
                typeChips("Actions", types.filter {
                    it.category == FlowItemCategory.SIDE_EFFECT || it.category == FlowItemCategory.EXPANDABLE
                })
                // Notes are edited inline in their rows; the dialog only creates them.
                // Loops are created here and configured in the drill-in sub-editor
                // (and cannot nest, hence hidden inside a body).
                if (!editing) div {
                    FieldLabel { +"Other" }
                    Stack {
                        direction = FlexDirection.row
                        css { gap = 6.px; marginTop = 6.px }
                        typeChip("Note", selectedKey == NOTE_KEY) { selectedKey = NOTE_KEY }
                        if (props.inLoopBody != true)
                            typeChip("Repeat over questions…", selectedKey == FOREACH_KEY) { selectedKey = FOREACH_KEY }
                    }
                }
            } else Stack {
                // Compact header: the chosen type + the way back to the chips.
                // (Changing type ~= resetting the step, so it stays deliberately
                // low-key — but some users will expect it, so it stays.)
                direction = FlexDirection.row
                css { alignItems = AlignItems.center; gap = 10.px }
                typeChip(selectedEntry?.label ?: when (selectedKey) {
                    FOREACH_KEY -> "Repeat over questions"
                    else -> "Note"
                }, selected = true) { selectedKey = null }
                ButtonUnstyled {
                    css {
                        fontSize = 12.px
                        fontFamily = sansSerif
                        color = Color("#888888")
                        textDecoration = TextDecoration.underline
                        cursor = Cursor.pointer
                    }
                    +"change type"
                    onClick = { selectedKey = null }
                }
            }

            if (!choosing && selectedKey == FOREACH_KEY) {
                Stack {
                    css { gap = 12.px; minWidth = 320.px }
                    div {
                        css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#888888") }
                        +"Starts over all room questions. Narrow the selection and add the repeated steps in the loop's own dialog, which opens next. Each step runs once per question, referencing it as the current question."
                    }
                }
            } else if (!choosing && selectedKey == NOTE_KEY) {
                Stack {
                    css { gap = 12.px; minWidth = 320.px }
                    FieldLabel { +"Note text" }
                    TextInput {
                        value = noteText
                        placeholder = "Shown only to you while presenting..."
                        onChange = { e -> noteText = e.target.value }
                    }
                    Stack {
                        direction = FlexDirection.row
                        css { alignItems = AlignItems.center; gap = 8.px }
                        Checkbox {
                            checked = noteWarning
                            onChange = { e -> noteWarning = e.target.checked }
                        }
                        span {
                            css { fontFamily = sansSerif; fontSize = 14.px }
                            +"Show as warning"
                        }
                    }
                }
            } else {
                // Render the selected type's editor (if any), grouped so
                // FormSection's large gap separates it from the chips as a whole
                // rather than spacing out every editor row.
                val action = currentAction
                if (selectedEntry?.editor != null && action != null) {
                    Stack {
                        css { gap = 12.px; minWidth = 320.px }
                        selectedEntry.editor {
                            this.action = action
                            this.onChange = { currentAction = it }
                        }
                    }
                }
            }
        }
    }
})

/** A selectable type chip, styled like the tag-filter chips in question management. */
private fun ChildrenBuilder.typeChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    button {
        css(ChipCSS) {
            cursor = Cursor.pointer
            if (selected) {
                backgroundColor = MainPalette.primary.color
                color = NamedColor.white
            } else {
                backgroundColor = Color("#F0F0F0")
                color = Color("#666666")
                border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
            }
        }
        +label
        onClick = { onSelect() }
    }
}
