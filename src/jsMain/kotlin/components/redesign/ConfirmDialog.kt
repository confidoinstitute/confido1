package components.redesign

import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.basic.sansSerif
import components.redesign.layout.LayoutModeContext
import web.cssom.FlexDirection
import web.cssom.Padding
import web.cssom.px
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div

external interface ConfirmDialogProps : PropsWithChildren {
    var open: Boolean
    var onClose: (() -> Unit)?
    var onConfirm: (() -> Unit)?
    var title: String
    /** Optional. Defaults to "Cancel". */
    var cancelText: String?
    /** Optional. Defaults to "Confirm". */
    var confirmText: String?
}

val ConfirmDialog = FC<ConfirmDialogProps> { props ->
    val layoutMode = use(LayoutModeContext)!!

    val cancelText = props.cancelText ?: "Cancel"
    val confirmText = props.confirmText ?: "Confirm"

    Dialog {
        open = props.open
        onClose = props.onClose
        title = props.title
        Stack {
            css {
                padding = Padding(20.px, 20.px, 0.px, 20.px)
            }
            div {
                css {
                    fontFamily = sansSerif
                }
                +props.children
            }
            Stack {
                if (layoutMode >= components.redesign.layout.LayoutMode.TABLET) {
                    direction = FlexDirection.row
                    css {
                        gap = 15.px
                        justifyContent = web.cssom.JustifyContent.end
                    }
                } else {
                    direction = FlexDirection.column
                }
                components.redesign.forms.Button {
                    css {
                        backgroundColor = web.cssom.Color("#F2F2F2")
                        color = web.cssom.Color("#000000")
                    }
                    onClick = { props.onClose?.invoke() }
                    +cancelText
                }
                components.redesign.forms.Button {
                    css {
                        backgroundColor = web.cssom.Color("#FF0000")
                    }
                    +confirmText
                    onClick = { props.onConfirm?.invoke() }
                }
            }
        }
    }
}