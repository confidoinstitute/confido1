package components.questions

import Client
import components.AppStateContext
import components.DistributionSummary
import components.IconToggleButton
import csstype.*
import dndkit.core.*
import dndkit.modifiers.restrictToVerticalAxis
import dndkit.modifiers.restrictToWindowEdges
import dndkit.sortable.*
import dndkit.utilities.CSS
import dndkit.utilities.closestCenter
import emotion.react.css
import hooks.useEditDialog
import icons.*
import kotlinx.js.jso
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.*
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionFlag
import payloads.requests.EditQuestionFieldType
import payloads.requests.ReorderQuestions
import react.*
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.abbr
import react.dom.html.ReactHTML.col
import react.dom.html.ReactHTML.colgroup
import react.dom.html.ReactHTML.span
import rooms.Room
import rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.state.havePermission
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

    // We maintain the order of questions separately to allow
    // for instant UI updates when swapping them around.
    // Note that the order is reversed (newest at the top)
    // TODO: If new questions are added, add them to the order
    var questionOrder by useState(props.questions.map { it.id }.toTypedArray())
    val questionOrderReversed = questionOrder.reversed().toTypedArray()

    val editQuestionOpen = useEditDialog(EditQuestionDialog)

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
    DndContext {
        collisionDetection = closestCenter
        modifiers = arrayOf(restrictToVerticalAxis, restrictToWindowEdges)
        this.sensors = sensors
        this.onDragEnd = { event ->
            // TODO: fix dynamic
            if (event.over != null && event.active.id != event.over.id) {
                val draggedId = event.active.id as String
                val overId = event.over.id as String

                // Apply the move immediately to make the UI feel responsive.
                val oldIndex = questionOrder.indexOf(draggedId)
                val newIndex = questionOrder.indexOf(overId)

                // We need to keep a copy of the moved array to send it right away,
                // questionOrder does not get updated until later.
                val moved = arrayMove(questionOrder, oldIndex, newIndex)
                questionOrder = moved

                // TODO: Add debounce
                val newOrder = moved.map { tools.confido.refs.Ref<Question>(it) }.toList()
                val reorder = ReorderQuestions(newOrder)
                Client.postData("/rooms/${room.id}/questions/reorder", reorder)
            }
        }
        TableContainer {
            // apparently, this is the only way? (https://stackoverflow.com/questions/4757844/css-table-column-autowidth)
            fun PropsWithClassName.autoSized() = css { width = 1.px; whiteSpace = WhiteSpace.nowrap }
            fun ChildrenBuilder.autoSizedCol() = col { autoSized() }
            component = Paper
            Table {
                colgroup {
                    col {}
                    autoSizedCol()
                    col {}
                    repeat(3) {
                        autoSizedCol()
                    }
                }
                TableHead {
                    TableRow {
                        TableCell {}
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
                SortableContext {
                    items = questionOrderReversed
                    strategy = verticalListSortingStrategy
                    TableBody {
                        questionOrderReversed.map { questionId ->
                            props.questions.find { it.id == questionId }?.let { question ->
                                QuestionRow {
                                    key = question.id
                                    this.question = question
                                    this.showGroupPredCol = showGroupPredCol
                                    this.showResolutionCol = showResolutionCol
                                    this.onEditDialog = editQuestionOpen
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (room.havePermission(RoomPermission.ADD_QUESTION)) {
        Button {
            this.startIcon = AddIcon.create()
            this.color = ButtonColor.primary
            this.disabled = stale
            onClick = { editQuestionOpen(null) }
            +"Add question…"
        }
    }
}

external interface QuestionRowProps : Props {
    var question: Question
    var showGroupPredCol: Boolean
    var showResolutionCol: Boolean
    var onEditDialog: ((Question) -> Unit)?
}

external interface DragHandleProps : Props {
    var isDragging: Boolean
    var listeners: dynamic // TODO: type
}

val DragHandle = FC<DragHandleProps> { props ->
    DragIndicatorIcon {
        role = AriaRole.button
        sx {
            verticalAlign = VerticalAlign.middle
            cursor = if (props.isDragging) Cursor.grabbing else Cursor.grab
        }
        color = SvgIconColor.action
        // TODO: Wrapper for this (+sortable.listeners)?
        // <div {...listeners} />
        // should be export type SyntheticListenerMap = Record<string, Function>;
        val keys = js("Object").keys(props.listeners).unsafeCast<Array<String>>()
        val listeners = props.listeners
        keys.map { this.asDynamic()[it] = listeners[it] }
    }
}

val QuestionRow = FC<QuestionRowProps> { props ->
    val question = props.question

    // TODO: Transform useSortable to builder?
    val sortable = useSortable(jso<UseSortableArguments> {
        this.id = props.question.id
    })

    fun postEditQuestion(id: String, field: EditQuestionFieldType, value: Boolean) {
        val editQuestion: EditQuestion = EditQuestionFlag(field, value)
        Client.postData("/questions/$id/edit", editQuestion)
    }

    TableRow {
        // TODO: This likely does not work because table row is a FC.
        // TODO: How to write this without dynamic?
        this.asDynamic().ref = sortable.setNodeRef
        css {
            // TODO: Get rid of asDynamic
            this.asDynamic().transform = CSS.transform.toString(sortable.transform)
            // XXX this was causing confusing visual artifacts, as seen here:
            // https://chat.confido.institute/file-upload/ikuaaPABuHgNjD7XR/reorder-questions-2022-12-06_23.04.39.webm
            //this.asDynamic().transition = sortable.transition
        }
        // TODO: apply sortable.attributes (a11y)

        // apparently, this is the only way? (https://stackoverflow.com/questions/4757844/css-table-column-autowidth)
        fun PropsWithClassName.autoSized() = css { width = 1.px; whiteSpace = WhiteSpace.nowrap }
        TableCell {
            sx {
                paddingLeft = themed(1)
                paddingRight = themed(0)
            }
            autoSized()
            DragHandle {
                // TODO apply sortable.setActivatorNodeRef
                listeners = sortable.listeners.asDynamic()
                isDragging = sortable.isDragging
            }
        }
        TableCell {
            autoSized()
            mui.material.Stack {
                direction = responsive(mui.material.StackDirection.row)
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
                        onClick = { props.onEditDialog?.invoke(question); it.stopPropagation() }
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