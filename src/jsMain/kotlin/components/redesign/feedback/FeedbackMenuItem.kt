package components.redesign.feedback

import components.redesign.FeedbackIcon
import components.redesign.basic.DialogMenuItem
import react.*

external interface FeedbackMenuItemProps : Props {
    /**
     * A user-facing name of the page this feedback relates to.
     *
     * Optional. When not provided, the feedback dialog will not have an option to attach the page name.
     */
    var pageName: String?
    var onClick: (() -> Unit)?
}

/**
 * A menu item that opens the feedback dialog.
 *
 * The feedback dialog is persisted within the [FeedbackContext] for ease of use.
 */
val FeedbackMenuItem = FC<FeedbackMenuItemProps> { props ->
    val feedback = useContext(FeedbackContext)

    DialogMenuItem {
        text = "Give feedback"
        icon = FeedbackIcon
        onClick = {
            feedback.open(props.pageName)
            props.onClick?.invoke()
        }
    }
}
