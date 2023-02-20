package components.redesign.questions

import components.AppStateContext
import components.redesign.*
import components.redesign.comments.Comment
import components.redesign.basic.*
import components.redesign.comments.AddCommentButton
import components.redesign.comments.AddCommentDialog
import components.redesign.comments.CommentInputVariant
import components.redesign.forms.TextButton
import components.showError
import csstype.*
import emotion.react.css
import hooks.useWebSocket
import io.ktor.http.*
import payloads.responses.CommentInfo
import payloads.responses.WSData
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.useContext
import react.useState
import tools.confido.question.Question
import tools.confido.refs.ref
import utils.questionUrl
import utils.runCoroutine
import web.prompts.confirm

external interface QuestionLayoutProps : Props {
    var question: Question
}

external interface QuestionHeaderProps : Props {
    var text: String
    var description: String
}

external interface QuestionStatusProps : Props {
    var text: String
}

external interface QuestionEstimateSectionProps : Props {

}

external interface QuestionEstimateTabButtonProps : Props {
    var text: String
    var active: Boolean
    var onClick: (() -> Unit)?
}

external interface QuestionCommentSectionProps : Props {
    var question: Question
}

external interface QuestionQuickSettingsDialogProps : Props {
    var question: Question
    var open: Boolean
    var onClose: (() -> Unit)?
}

private val bgColor = Color("#f2f2f2")

val QuestionPage = FC<QuestionLayoutProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val myPrediction = appState.myPredictions[props.question.ref]

    var quickSettingsOpen by useState(false)

    QuestionQuickSettingsDialog {
        question = props.question
        open = quickSettingsOpen
        onClose = { quickSettingsOpen = false }
    }

    // TODO: Replace with a navbar "more" button
    TextButton {
        onClick = { quickSettingsOpen = true }
        +"Open quick settings (will be replaced by a navbar button)"
    }

    Stack {
        QuestionHeader {
            this.text = props.question.name
            this.description = props.question.description
        }
        QuestionEstimateSection {
            // TODO: connect to the question
        }
        QuestionCommentSection {
            this.question = props.question
        }
    }
}

private val QuestionEstimateTabButton = FC<QuestionEstimateTabButtonProps> { props ->
    button {
        css {
            all = Globals.unset
            cursor = Cursor.pointer

            borderRadius = 10.px
            padding = Padding(12.px, 10.px)

            flexGrow = number(1.0)
            textAlign = TextAlign.center

            fontFamily = FontFamily.sansSerif
            fontSize = 17.px
            lineHeight = 21.px

            if (props.active) {
                backgroundColor = Color("#FFFFFF")
                color = Color("#000000")
                fontWeight = integer(500)
            } else {
                color = Color("rgba(0, 0, 0, 0.5)")
                fontWeight = FontWeight.normal
            }
        }
        onClick = { props.onClick?.invoke() }

        +props.text
    }
}

private val QuestionEstimateSection = FC<QuestionEstimateSectionProps> { props ->
    // false = your estimate open
    // true = group estimate open
    var groupEstimateOpen by useState(false)

    // Tabs
    Stack {
        css {
            padding = 15.px
            background = bgColor
            borderRadius = 5.px
        }
        direction = FlexDirection.row
        QuestionEstimateTabButton {
            text = "Your estimate"
            active = !groupEstimateOpen
            onClick = { groupEstimateOpen = false }
        }
        QuestionEstimateTabButton {
            text = "Group estimate"
            active = groupEstimateOpen
            onClick = { groupEstimateOpen = true }
        }
    }

    div {
        css {
            height = 196.px
            backgroundColor = Color("#fff")
        }
        if (!groupEstimateOpen) {
            // TODO: Your estimate
            +"TODO your estimate goes here"
        } else {
            // TODO: Group estimate
            +"TODO group estimate goes here"
        }
    }
    Stack {
        // TODO: Section describing the estimate
        css {
            padding = Padding(25.px, 15.px)
            gap = 10.px
        }
        div {
            +"This helpful section describes your estimate or the group estimate and how many people participated."
        }
        div {
            +"This still needs to be implemented."
        }
    }
}

private val QuestionHeader = FC<QuestionHeaderProps> { props ->
    Stack {
        css {
            padding = Padding(20.px, 15.px, 5.px)
            gap = 4.px
            background = bgColor
        }

        // Question text
        div {
            css {
                fontFamily = FontFamily.serif
                fontWeight = FontWeight.bold
                fontSize = 34.px
                lineHeight = 105.pct
                color = Color("#000000")
            }
            +props.text
        }

        // Question status
        // TODO: Implement on backend
        //QuestionStatusLine {
        //    text = "Opened 10 feb 2023, 12:00"
        //}
        //QuestionStatusLine {
        //    text = "Closing 26 feb 2023, 12:00"
        //}
        //QuestionStatusLine {
        //    text = "Resolving 28 feb 2023, 12:00"
        //}

        // Question description
        div {
            css {
                padding = Padding(10.px, 0.px, 0.px)
                fontFamily = FontFamily.sansSerif
                fontSize = 15.px
                lineHeight = 18.px
                color = Color("#000000")
            }
            // TODO: clamp and show "See more" if text too long
            TextWithLinks {
                text = props.description
            }
        }
    }
}

private val QuestionCommentSection = FC<QuestionCommentSectionProps> { props ->
    val comments = useWebSocket<Map<String, CommentInfo>>("/state${props.question.urlPrefix}/comments")

    var addCommentOpen by useState(false)

    AddCommentDialog {
        open = addCommentOpen
        onClose = { addCommentOpen = false }
        id = props.question.id
        variant = CommentInputVariant.QUESTION
    }

    Stack {
        css {
            justifyContent = JustifyContent.spaceBetween
            padding = Padding(12.px, 13.px, 13.px, 15.px)
            backgroundColor = bgColor
        }
        direction = FlexDirection.row
        div {
            css {
                textTransform = TextTransform.uppercase
                fontFamily = FontFamily.sansSerif
                fontSize = 13.px
                lineHeight = 16.px
                color = Color("#777777")
            }
            +"Comments"
        }
        SortButton { }
    }

    Stack {
        css {
            gap = 8.px
        }

        when (comments) {
            is WSData -> comments.data.entries.sortedByDescending { it.value.comment.timestamp }.map {
                Comment {
                    commentInfo = it.value
                    key = it.key
                }
            }

            else -> {
                // TODO: Proper loading design
                +"Loading..."
            }
        }

        AddCommentButton {
            onClick = { addCommentOpen = true }
        }
    }
}

private val QuestionStatusLine = FC<QuestionStatusProps> { props ->
    div {
        css {
            fontFamily = FontFamily.sansSerif
            fontWeight = FontWeight.bold
            fontSize = 12.px
            lineHeight = 15.px
            color = Color("rgba(0, 0, 0, 0.3)")

            padding = Padding(2.px, 0.px)
            textTransform = TextTransform.uppercase
        }
        +props.text
    }
}

private val QuestionQuickSettingsDialog = FC<QuestionQuickSettingsDialogProps> { props ->
    fun delete() = runCoroutine {
        Client.send(
            questionUrl(props.question.id),
            HttpMethod.Delete,
            onError = { showError?.invoke(it) }) {
            // TODO: Navigate to the room page
            props.onClose?.invoke()
        }
    }

    DialogMenu {
        open = props.open
        onClose = { props.onClose?.invoke() }
        /*
        DialogMenuItem {
            text = "Hide"
            disabled = true
            onClick = {
                // TODO: Implement and remove disabled
            }
        }
        DialogMenuItem {
            text = "Close"
            disabled = true
            onClick = {
                // TODO: Implement and remove disabled
            }
        }
        DialogMenuItem {
            text = "Resolve"
            disabled = true
            onClick = {
                // TODO: Implement and remove disabled
            }
        }
        DialogMenuSeparator {}
         */
        DialogMenuItem {
            text = "Edit this question"
            icon = EditIcon
            disabled = true
            onClick = {
                // TODO: Implement and remove disabled
            }
        }
        DialogMenuItem {
            text = "Delete this question"
            icon = BinIcon
            variant = DialogMenuItemVariant.dangerous
            onClick = {
                // TODO: Check for confirmation properly
                if (confirm("Are you sure you want to delete the question? This action is irreversible. Deleting will also result in loss of all predictions made for this question.")) {
                    delete()
                }
            }
        }
        /*
        DialogMenuSeparator {}
        DialogMenuItem {
            text = "How to use this page"
            disabled = true
        }
         */
    }
}