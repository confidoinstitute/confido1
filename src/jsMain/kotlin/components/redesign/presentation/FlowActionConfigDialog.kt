package components.redesign.presentation

import components.redesign.basic.*
import components.redesign.forms.Select
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.option
import web.cssom.*

/**
 * Generic dialog for configuring a [tools.confido.state.FlowAction].
 * Populates the action type dropdown from [flowActionTypes] registry
 * and delegates configuration UI to the selected type's editor component.
 */
external interface FlowActionConfigDialogProps : Props {
    var action: tools.confido.state.FlowAction?
    var open: Boolean
    var onClose: (() -> Unit)?
    var onConfirm: ((tools.confido.state.FlowAction) -> Unit)?
}

val FlowActionConfigDialog = FC<FlowActionConfigDialogProps> { props ->
    val types = flowActionTypes

    var selectedKey by useState(types.firstOrNull()?.key ?: "")
    var currentAction by useState<tools.confido.state.FlowAction?>(null)

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
        title = if (props.action != null) "Edit action" else "Add action"
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

            FieldLabel { +"Action type" }
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

            // Render the selected type's editor
            val action = currentAction
            if (selectedEntry != null && action != null) {
                selectedEntry.editor {
                    this.action = action
                    this.onChange = { currentAction = it }
                }
            }
        }
    }
}
