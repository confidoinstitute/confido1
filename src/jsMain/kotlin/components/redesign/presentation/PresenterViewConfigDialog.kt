package components.redesign.presentation

import components.redesign.basic.*
import components.redesign.forms.Select
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.option
import tools.confido.state.PresenterView
import web.cssom.*

/**
 * Generic dialog for configuring a [PresenterView].
 * Populates the view type dropdown from [presenterViewTypes] registry
 * and delegates configuration UI to the selected type's editor component.
 */
external interface PresenterViewConfigDialogProps : Props {
    var view: PresenterView?
    var open: Boolean
    var onClose: (() -> Unit)?
    var onConfirm: ((PresenterView) -> Unit)?
}

val PresenterViewConfigDialog = FC<PresenterViewConfigDialogProps> { props ->
    val types = presenterViewTypes

    var selectedKey by useState(types.firstOrNull()?.key ?: "")
    var currentView by useState<PresenterView?>(null)

    // Reset state when dialog opens
    useEffect(props.open, props.view) {
        if (props.open) {
            val existing = props.view
            if (existing != null) {
                val entry = types.find { it.matches(existing) }
                selectedKey = entry?.key ?: types.firstOrNull()?.key ?: ""
                currentView = existing
            } else {
                val first = types.firstOrNull()
                selectedKey = first?.key ?: ""
                currentView = first?.createDefault()
            }
        }
    }

    val selectedEntry = types.find { it.key == selectedKey }

    Dialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = if (props.view != null) "Edit presenter view" else "Add presenter view"
        action = "OK"
        disabledAction = currentView == null
        onAction = {
            currentView?.let { props.onConfirm?.invoke(it) }
            props.onClose?.invoke()
        }

        Stack {
            css {
                gap = 16.px
                padding = Padding(8.px, 0.px)
                minWidth = 320.px
            }

            FieldLabel { +"View type" }
            Select {
                value = selectedKey
                onChange = { e ->
                    val newKey = e.target.value
                    selectedKey = newKey
                    currentView = types.find { it.key == newKey }?.createDefault()
                }
                css { width = 100.pct }
                types.forEach { entry ->
                    option { value = entry.key; +entry.label }
                }
            }

            // Render the selected type's editor (if any)
            val view = currentView
            if (selectedEntry?.editor != null && view != null) {
                selectedEntry.editor {
                    this.view = view
                    this.onChange = { currentView = it }
                }
            }
        }
    }
}
