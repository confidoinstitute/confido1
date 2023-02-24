package components.redesign.questions

import components.AppStateContext
import components.redesign.*
import components.redesign.comments.Comment
import components.redesign.basic.*
import components.redesign.comments.AddCommentButton
import components.redesign.comments.AddCommentDialog
import components.redesign.comments.CommentInputVariant
import components.redesign.rooms.RoomNavbar
import components.rooms.RoomContext
import components.showError
import csstype.*
import emotion.react.css
import hooks.useDocumentTitle
import hooks.useWebSocket
import io.ktor.http.*
import payloads.responses.CommentInfo
import payloads.responses.WSData
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.router.useNavigate
import react.useContext
import react.useState
import rooms.RoomPermission
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.spaces.Value
import utils.questionUrl
import utils.roomUrl
import utils.runCoroutine
import web.prompts.confirm

external interface QuestionLayoutProps : Props {
    var question: Question
}

external interface QuestionHeaderProps : Props {
    var text: String
    var description: String
    var resolution: Value?
}

external interface QuestionStatusProps : Props {
    var text: String
}

external interface QuestionEstimateSectionProps : Props {
    var resolved: Boolean
    var myPrediction: Prediction?
    var groupPrediction: Prediction?
    var numPredictors: Int
}

external interface QuestionEstimateTabButtonProps : Props {
    var text: String
    var active: Boolean
    var onClick: (() -> Unit)?
}

external interface QuestionCommentSectionProps : Props {
    var question: Question
    var myPrediction: Prediction?
}

external interface QuestionQuickSettingsDialogProps : Props {
    var question: Question
    var open: Boolean
    var onClose: (() -> Unit)?
}

private val bgColor = Color("#f2f2f2")

val QuestionPage = FC<QuestionLayoutProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val myPrediction = appState.myPredictions[props.question.ref]
    val groupPrediction = useWebSocket<Prediction?>("/state${props.question.urlPrefix}/group_pred")

    var quickSettingsOpen by useState(false)

    useDocumentTitle("${props.question.name} - Confido")

    if (!room.hasPermission(appState.session.user, RoomPermission.VIEW_QUESTIONS))
    // TODO proper no permission page
        return@FC

    QuestionQuickSettingsDialog {
        question = props.question
        open = quickSettingsOpen
        onClose = { quickSettingsOpen = false }
    }

    RoomNavbar {
        navigateBack = room.urlPrefix
        onMenu = { quickSettingsOpen = true }
        +room.name
    }
    Stack {
        component = main
        css {
            marginTop = 44.px
            flexDirection = FlexDirection.column
            flexGrow = number(1.0)
        }

        QuestionHeader {
            this.text = props.question.name
            this.description = props.question.description
            if (props.question.resolutionVisible) {
                this.resolution = props.question.resolution
            }
        }
        QuestionPredictionSection {
            this.resolved = props.question.resolved
            this.myPrediction = myPrediction
            this.numPredictors = props.question.numPredictors
            this.groupPrediction = groupPrediction.data
        }
        QuestionCommentSection {
            this.question = props.question
            this.myPrediction = myPrediction
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

private val QuestionPredictionSection = FC<QuestionEstimateSectionProps> { props ->
    // false = your estimate open
    // true = group estimate open
    var groupPredictionOpen by useState(false)

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
            active = !groupPredictionOpen
            onClick = { groupPredictionOpen = false }
        }
        QuestionEstimateTabButton {
            text = "Group estimate"
            active = groupPredictionOpen
            onClick = { groupPredictionOpen = true }
        }
    }

    div {
        css {
            height = 196.px
            backgroundColor = Color("#fff")
        }
        if (!groupPredictionOpen) {
            // TODO: Your estimate
            +"TODO your estimate goes here"
        } else {
            // TODO: Group estimate
            +"TODO group estimate goes here"
        }
    }
    if (groupPredictionOpen) {
        GroupPredictionDescription {
            this.prediction = props.groupPrediction
            this.myPredictionExists = props.myPrediction != null
            this.resolved = props.resolved
            this.numPredictors = props.numPredictors
        }
    } else {
        MyPredictionDescription {
            this.prediction = props.myPrediction
            this.resolved = props.resolved
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

        // Resolution
        props.resolution?.let {
            QuestionStatusLine {
                // TODO: time of resolution (requires backend)
                text = "Resolved"
            }
            div {
                css {
                    fontFamily = FontFamily.serif
                    fontWeight = FontWeight.bold
                    fontSize = 34.px
                    lineHeight = 100.pct
                    color = Color("#00CC2E")
                }
                +it.format()
            }
        }

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
    var sortType by useState(SortType.NEWEST)

    AddCommentDialog {
        open = addCommentOpen
        onClose = { addCommentOpen = false }
        id = props.question.id
        variant = CommentInputVariant.QUESTION
        prediction = props.myPrediction
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
        if (comments.data?.isNotEmpty() == true) {
            SortButton {
                options = listOf(SortType.NEWEST, SortType.OLDEST)
                this.sortType = sortType
                onChange = { sort -> sortType = sort }
            }
        }
    }

    Stack {
        css {
            flexGrow = number(1.0)
            gap = 8.px
        }

        when (comments) {
            is WSData -> {
                val sortedComments = when (sortType) {
                    SortType.NEWEST -> comments.data.entries.sortedByDescending { it.value.comment.timestamp }
                    SortType.OLDEST -> comments.data.entries.sortedBy { it.value.comment.timestamp }
                    else -> emptyList()
                }
                sortedComments.map {
                    Comment {
                        commentInfo = it.value
                        key = it.key
                    }
                }
            }

            else -> {
                // TODO: Proper loading design
                +"Loading..."
            }
        }
    }

    AddCommentButton {
        onClick = { addCommentOpen = true }
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
    val room = useContext(RoomContext)
    val navigate = useNavigate()

    fun delete() = runCoroutine {
        Client.send(
            questionUrl(props.question.id),
            HttpMethod.Delete,
            onError = { showError?.invoke(it) }) {
            navigate(room.urlPrefix)
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