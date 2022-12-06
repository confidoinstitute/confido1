package components.questions

import Client
import components.AppStateContext
import components.DistributionSummary
import components.IconToggleButton
import csstype.*
import dndkit.core.*
import dndkit.sortable.*
import dndkit.utilities.CSS
import dndkit.utilities.Transform
import emotion.react.css
import hooks.useEditDialog
import icons.*
import kotlinx.browser.window
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.*
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionFlag
import payloads.requests.EditQuestionFieldType
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.abbr
import react.dom.html.ReactHTML.col
import react.dom.html.ReactHTML.colgroup
import react.dom.html.ReactHTML.span
import rooms.Room
import rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.refs.HasId
import tools.confido.state.havePermission
import tools.confido.utils.randomString
import utils.*

external interface QuestionTableProps : Props {
    var room: Room
    var questions: List<Question>
    var allowEditingQuestions: Boolean
}


val QuestionTable = FC<QuestionTableProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    val room = props.room

    val mouseSensor = useSensor<MouseSensorOptions>(MouseSensor)
    val sensors = useSensors(mouseSensor)

    val showGroupPredCol = (
            props.questions.any{it.groupPred != null}
                    && (
                        room.havePermission(RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
                        || props.questions.any{it.groupPredVisible}
                        )
            )
    val showResolutionCol = (
            props.questions.any{it.resolution != null}
                    && (
                    room.havePermission(RoomPermission.VIEW_ALL_RESOLUTIONS)
                            || props.questions.any{it.resolutionVisible}
                    )
            )

    Typography {
        this.variant = TypographyVariant.body1
        css { fontStyle = FontStyle.italic; fontSize = FontSize.smaller; paddingBottom = 5.px; }
        +"This table gives a quick and condensed moderator overview of the questions in the room."
        +"It also allows for basic question management such as showing/hiding questions."
        +"If you hover your mouse cursor over an icon or other similar element, an explanation of its function will be shown."
    }
    TableContainer {
        // apparently, this is the only way? (https://stackoverflow.com/questions/4757844/css-table-column-autowidth)
        fun PropsWithClassName.autoSized() = css { width = 1.px; whiteSpace = WhiteSpace.nowrap }
        fun ChildrenBuilder.autoSizedCol() = col { autoSized() }
        component = Paper
        Table {
            colgroup {
                autoSizedCol()
                col {}
                repeat(3) {
                    autoSizedCol()
                }
            }
            TableHead {
                TableRow {
                    TableCell { autoSized() }
                    TableCell { +"Question" }
                    TableCell {
                        autoSized()
                        abbr {
                            title = "Number of people predicting, total number of prediction updates"
                            span {
                                css { whiteSpace = WhiteSpace.nowrap }
                                GroupsIcon { fontSize = SvgIconSize.inherit }
                                +" / "
                                TimelineIcon { fontSize = SvgIconSize.inherit }
                            }
                        }
                    }
                    if (showGroupPredCol) TableCell { autoSized(); +"Group pred." }
                    if (showResolutionCol) TableCell { autoSized(); +"Resolution" }
                }
            }
            TableBody {
                DndContext {
                    this.sensors = sensors
                    this.onDragEnd = { event ->
                        // TODO: fix dynamic
                        if (event.over != null && event.active.id != event.over.id) {
                            // TODO: Swap
                            window.alert("swap ${event.active.id} with ${event.over.id}")
                        }
                    }
                    SortableContext {
                        items = props.questions.map { it.id }.toTypedArray()
                        console.log(verticalListSortingStrategy)
                        strategy = verticalListSortingStrategy
                        props.questions.map { question ->
                            QuestionRow {
                                this.question = question
                                this.showGroupPredCol = showGroupPredCol
                                this.showResolutionCol = showResolutionCol
                            }
                        }
                    }
                }
            }
        }
    }
}

external interface QuestionRowProps : Props {
    var question: Question
    var showGroupPredCol: Boolean
    var showResolutionCol: Boolean
}

val QuestionRow = FC<QuestionRowProps> { props ->
    val question = props.question

    // TODO: Transform useSortable to builder?
    val sortable = useSortable(jsObject {
        this.id = props.question.id
    }.unsafeCast<UseSortableArguments>())

    fun postEditQuestion(id: String, field: EditQuestionFieldType, value: Boolean) {
        val editQuestion: EditQuestion = EditQuestionFlag(field, value)
        Client.postData("/questions/$id/edit", editQuestion)
    }

    val editQuestionOpen = useEditDialog(EditQuestionDialog)


    TableRow {
        // TODO: is this valid?
        this.asDynamic().ref = sortable.setNodeRef
        css {
            // TODO: Get rid of asDynamic
            this.asDynamic().transform = CSS.transform.toString(sortable.transform)
            this.asDynamic().transition = sortable.transition
        }
        // TODO: sortable.attributes (a11y)

        // TODO: Wrapper for this (+sortable.listeners)?
        // <div {...listeners} />
        // should be export type SyntheticListenerMap = Record<string, Function>;
        val keys = js("Object").keys(sortable.listeners).unsafeCast<Array<String>>()
        val listeners = sortable.listeners.asDynamic()
        keys.map { this.asDynamic()[it] = listeners[it] }

        // apparently, this is the only way? (https://stackoverflow.com/questions/4757844/css-table-column-autowidth)
        fun PropsWithClassName.autoSized() = css { width = 1.px; whiteSpace = WhiteSpace.nowrap }
        fun ChildrenBuilder.autoSizedCol() = col { autoSized() }
        TableCell {
            autoSized()
            Stack {
                direction = responsive(StackDirection.row)
                Tooltip {
                    title = breakLines(
                        if (question.visible) "Question is visible to forecasters.\nClick to make it hidden."
                        else "Question is hidden from forecasters.\nClick to make it visible."
                    )
                    arrow = true
                    ReactHTML.span {
                        IconToggleButton {
                            on = question.visible
                            onIcon = VisibilityIcon.create()
                            offIcon = VisibilityOffOutlinedIcon.create()
                            onChange = {
                                postEditQuestion(
                                    question.id,
                                    payloads.requests.EditQuestionFieldType.VISIBLE,
                                    it
                                )
                            }
                        }
                    }
                }
                Tooltip {
                    title = breakLines(
                        if (question.open) "Question is open for new predictions.\nClick to make it closed."
                        else "Question is closed (new predictions are not allowed).\nClick to make it open."
                    )
                    arrow = true
                    ReactHTML.span {
                        IconToggleButton {
                            on = question.open
                            onIcon = LockOpenIcon.create()
                            offIcon = LockIcon.create()
                            onChange = {
                                postEditQuestion(
                                    question.id,
                                    payloads.requests.EditQuestionFieldType.OPEN,
                                    it
                                )
                            }
                        }
                    }
                }
                Tooltip {
                    title = ReactNode("Edit question")
                    IconButton {
                        EditIcon()
                        onClick = { editQuestionOpen(question) }
                    }
                }
            }
        }
        TableCell { +question.name }
        TableCell {
            autoSized()
            +"${question.numPredictors} "
            +" / "
            +"${question.numPredictions}"
        }
        if (props.showGroupPredCol)
            TableCell {
                autoSized()
                Tooltip {
                    arrow = true
                    title = breakLines(
                        if (question.groupPredVisible)
                            "Group prediction is visible to forecasters.\nClick to make it hidden."
                        else
                            "Group prediction is hidden from forecasters.\nClick to make it visible."
                    )
                    span {
                        IconToggleButton {
                            on = question.groupPredVisible
                            onIcon = VisibilityIcon.create()
                            offIcon = VisibilityOffOutlinedIcon.create()
                            onChange = {
                                postEditQuestion(
                                    question.id,
                                    payloads.requests.EditQuestionFieldType.GROUP_PRED_VISIBLE,
                                    it
                                )
                            }
                        }
                    }
                }
                question.groupPred?.dist?.let { groupDist ->
                    DistributionSummary {
                        distribution = groupDist
                        allowPlotDialog = true
                    }
                }
            }
        if (props.showResolutionCol)
            TableCell {
                autoSized()
                question.resolution?.let {
                    Tooltip {
                        arrow = true
                        title = breakLines(
                            if (question.resolutionVisible)
                                "Resolution is visible to forecasters.\nClick to make it hidden."
                            else
                                "Resolution is hidden from forecasters.\nClick to make it visible."
                        )
                        span {
                            IconToggleButton {
                                on = question.resolutionVisible
                                onIcon = VisibilityIcon.create()
                                offIcon = VisibilityOffOutlinedIcon.create()
                                onChange = {
                                    postEditQuestion(
                                        question.id,
                                        payloads.requests.EditQuestionFieldType.RESOLUTION_VISIBLE,
                                        it
                                    )
                                }
                            }
                        }
                    }
                    +it.format()
                }
            }
    }
}