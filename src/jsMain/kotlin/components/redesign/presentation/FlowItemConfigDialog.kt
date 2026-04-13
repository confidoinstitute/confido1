package components.redesign.presentation

import components.redesign.basic.*
import components.redesign.forms.Select
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.option
import tools.confido.state.FlowAction
import web.cssom.*

/**
 * Unified dialog for configuring a [FlowAction] (covering both side-effect
 * actions and presenter view actions). Populates the type dropdown from the
 * [flowItemTypes] registry and delegates configuration UI to the selected
 * type's editor component.
 */
external interface FlowItemConfigDialogProps : Props {
    var action: FlowAction?
    var open: Boolean
    var onClose: (() -> Unit)?
    var onConfirm: ((FlowAction) -> Unit)?
}

val FlowItemConfigDialog = FC<FlowItemConfigDialogProps> { props ->
    val types = flowItemTypes

    var selectedKey by useState(types.firstOrNull()?.key ?: "")
    var currentAction by useState<FlowAction?>(null)

    // Reset state when dialog opens
    useEffect(props.open, props.action) {
        if (props.open) {
            val existing = props.action
            if (existing != null) {
                val entry = types.find { it.matches(existing) }
                selectedKey = entry?.key ?: types.firstOrNull()?.key ?: ""
                currentAction = existing
            } else {
                val first = types.firstOrNull()
                selectedKey = first?.key ?: ""
                currentAction = first?.createDefault()
            }
        }
    }

    val selectedEntry = types.find { it.key == selectedKey }

    Dialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = if (props.action != null) "Edit item" else "Add item"
        action = "OK"
        disabledAction = currentAction == null
        onAction = {
            currentAction?.let { props.onConfirm?.invoke(it) }
            props.onClose?.invoke()
        }

        Stack {
            css {
                gap = 16.px
                padding = Padding(8.px, 0.px)
                minWidth = 320.px
            }

            FieldLabel { +"Type" }
            Select {
                value = selectedKey
                onChange = { e ->
                    val newKey = e.target.value
                    selectedKey = newKey
                    currentAction = types.find { it.key == newKey }?.createDefault()
                }
                css { width = 100.pct }
                types.forEach { entry ->
                    option { value = entry.key; +entry.label }
                }
            }

            // Render the selected type's editor (if any)
            val action = currentAction
            if (selectedEntry?.editor != null && action != null) {
                selectedEntry.editor {
                    this.action = action
                    this.onChange = { currentAction = it }
                }
            }
        }
    }
}
