package components

import mui.material.*
import mui.material.styles.TypographyVariant
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.sup
import react.useState

external interface ExplanationProps : PropsWithChildren {
    var title: String
}

val Explanation = FC<ExplanationProps> {props ->
    var explanationOpen by useState(false)

    Link {
        component = button
        variant = TypographyVariant.body1
        onClick = {explanationOpen = true}
       sup {
           +"[?]"
       }
    }
    Dialog {
        open = explanationOpen
        onClose = {_, _ -> explanationOpen = false}
        scroll = DialogScroll.paper
        DialogTitle {
            + props.title
        }
        DialogContent {
            this.children = props.children
        }
        DialogActions {
            Button {
                onClick = {explanationOpen = false}
                +"Understood"
            }
        }
    }
}
