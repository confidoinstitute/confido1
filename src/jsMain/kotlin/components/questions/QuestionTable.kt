package components.questions

import Client
import components.AppStateContext
import components.DistributionSummary
import components.IconToggleButton
import components.showError
import csstype.*
import dndkit.applyListeners
import dndkit.core.*
import dndkit.modifiers.restrictToVerticalAxis
import dndkit.modifiers.restrictToWindowEdges
import dndkit.sortable.*
import dndkit.utilities.CSS
import dndkit.utilities.closestCenter
import emotion.react.css
import hooks.useEditDialog
import hooks.useWebSocket
import icons.*
import kotlinx.js.jso
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionFieldType
import payloads.requests.EditQuestionFlag
import payloads.requests.ReorderQuestions
import payloads.responses.WSError
import react.*
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.abbr
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.col
import react.dom.html.ReactHTML.colgroup
import react.dom.html.ReactHTML.span
import rooms.Room
import rooms.RoomPermission
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.state.havePermission
import utils.questionUrl
import utils.runCoroutine
import utils.themed

external interface QuestionTableProps : Props {
    var room: Room
    var questions: List<Question>
    var allowEditingQuestions: Boolean
}


val QuestionTable = FC<QuestionTableProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    val room = props.room

    val groupPredsWS = useWebSocket<Map<String, Prediction?>>("/state${room.urlPrefix}/group_pred")
    val groupPreds = groupPredsWS.data ?: emptyMap()

    val mouseSensor = useSensor<MouseSensorOptions>(MouseSensor)
    val sensors = useSensors(mouseSensor)

    // We maintain the order of questions separately to allow
    // for instant UI updates when swapping them around.
    // Note that the order is reversed (newest at the top)
    var questionOrder by useState(props.questions.map { it.id }.toTypedArray())
    val questionOrderReversed = questionOrder.reversed().toTypedArray()

    // Update the saved question order with new or removed questions (the order is derived state)
    if (props.questions.map { it.id }.toSet() != questionOrder.toSet()) {
        val newQuestionIds = props.questions.map { it.id }.filter { !questionOrder.contains(it) }
        val nonRemovedIds = questionOrder.filter { storedId -> props.questions.any { it.id == storedId } }
        questionOrder = (nonRemovedIds + newQuestionIds).toTypedArray()
    }

    val editQuestionOpen = useEditDialog(EditQuestionDialog)

    val showGroupPredCol = (
            props.questions.any{it.numPredictions > 0}
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

    if (groupPredsWS is WSError) {
        Alert {
            severity = AlertColor.error
            +groupPredsWS.prettyMessage
        }
    }

    DndContext {
        collisionDetection = closestCenter
        modifiers = arrayOf(restrictToVerticalAxis, restrictToWindowEdges)
        this.sensors = sensors
        this.onDragEnd = { event ->
            val over = event.over
            if (over != null && event.active.id != over.id) {
                val draggedId = event.active.id
                val overId = over.id

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
                runCoroutine {
                    Client.sendData("${room.urlPrefix}/questions/reorder", reorder, onError = {showError(it)}) {}
                }
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
                                    this.groupPred = groupPreds.get(question.id)
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
    var groupPred: Prediction?
    var showResolutionCol: Boolean
    var onEditDialog: ((Question) -> Unit)?
}

external interface DragHandleProps : Props {
    var isDragging: Boolean
    var listeners: SyntheticListenerMap?
}

val DragHandle = FC<DragHandleProps> { props ->
    DragIndicatorIcon {
        role = AriaRole.button
        sx {
            verticalAlign = VerticalAlign.middle
            cursor = if (props.isDragging) Cursor.grabbing else Cursor.grab
        }
        color = SvgIconColor.action
        applyListeners(props.listeners)
    }
}

val QuestionRow = FC<QuestionRowProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    val question = props.question

    val sortable = useSortable(jso<UseSortableArguments> {
        this.id = props.question.id
    })

    fun toggleTooltip(on: Boolean, onText: String, onClickText: String, offText: String, offClickText: String) =
        if (stale) {
            ReactNode(if (on) onText else offText)
        } else {
            Fragment.create {
                if (on) +onText else +offText
                br {}
                if (on) +onClickText else +offClickText
            }
        }

    fun postEditQuestion(question: Question, field: EditQuestionFieldType, value: Boolean) = runCoroutine {
        val editQuestion: EditQuestion = EditQuestionFlag(field, value)
        Client.sendData("${question.urlPrefix}/edit", editQuestion, onError = {showError(it)}) {}
    }

    TableRow {
        this.asDynamic().ref = sortable.setNodeRef
        css {
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
            if (!stale)
            DragHandle {
                // TODO apply sortable.setActivatorNodeRef (requires forwardref in DragHandle)
                listeners = sortable.listeners
                isDragging = sortable.isDragging
            }
        }
        TableCell {
            autoSized()
            mui.material.Stack {
                direction = responsive(mui.material.StackDirection.row)
                Tooltip {
                    title = toggleTooltip(question.visible,
                        "Question is visible to forecasters.",
                        "Click to make it hidden.",
                        "Question is hidden from forecasters.",
                        "Click to make it visible."
                    )
                    arrow = true
                    span {
                        IconToggleButton {
                            on = question.visible
                            onIcon = VisibilityIcon.create()
                            offIcon = VisibilityOffOutlinedIcon.create()
                            disabled = stale
                            onChange = {
                                postEditQuestion(
                                    question,
                                    payloads.requests.EditQuestionFieldType.VISIBLE,
                                    it
                                )
                            }
                        }
                    }
                }
                Tooltip {
                    title = toggleTooltip(question.open,
                        "Question is open for new predictions.",
                        "Click to make it closed.",
                        "Question is closed (new predictions are not allowed).",
                        "Click to make it open.",
                    )
                    arrow = true
                    span {
                        IconToggleButton {
                            on = question.open
                            onIcon = LockOpenIcon.create()
                            offIcon = LockIcon.create()
                            disabled = stale
                            onChange = {
                                postEditQuestion(
                                    question,
                                    payloads.requests.EditQuestionFieldType.OPEN,
                                    it
                                )
                            }
                        }
                    }
                }
                Tooltip {
                    title = ReactNode("Edit question")
                    arrow = true
                    span {
                        IconButton {
                            EditIcon()
                            disabled = stale
                            onClick = { props.onEditDialog?.invoke(question); it.stopPropagation() }
                        }
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
                    title = toggleTooltip(question.groupPredVisible,
                        "Group prediction is visible to forecasters.",
                        "Click to make it hidden.",
                        "Group prediction is hidden from forecasters.",
                        "Click to make it visible.",
                    )
                    span {
                        IconToggleButton {
                            on = question.groupPredVisible
                            onIcon = VisibilityIcon.create()
                            offIcon = VisibilityOffOutlinedIcon.create()
                            disabled = stale
                            onChange = {
                                postEditQuestion(
                                    question,
                                    payloads.requests.EditQuestionFieldType.GROUP_PRED_VISIBLE,
                                    it
                                )
                            }
                        }
                    }
                }
                props.groupPred?.dist?.let { groupDist ->
                    DistributionSummary {
                        distribution = groupDist
                        allowPlotDialog = true
                    }
                } ?: run {
                    if (question.numPredictions > 0)
                        Skeleton {
                            sx {
                                display = Display.inlineBlock
                                width = 3.rem
                            }
                        }
                }
            }
        if (props.showResolutionCol)
            TableCell {
                autoSized()
                question.resolution?.let {
                    Tooltip {
                        arrow = true
                        title = toggleTooltip(question.resolutionVisible,
                            "Resolution is visible to forecasters.",
                            "Click to make it hidden.",
                            "Resolution is hidden from forecasters.",
                            "Click to make it visible.",
                        )
                        span {
                            IconToggleButton {
                                on = question.resolutionVisible
                                onIcon = VisibilityIcon.create()
                                offIcon = VisibilityOffOutlinedIcon.create()
                                disabled = stale
                                onChange = {
                                    postEditQuestion(
                                        question,
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