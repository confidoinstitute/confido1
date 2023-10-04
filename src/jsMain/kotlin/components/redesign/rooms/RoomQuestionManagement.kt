package components.redesign.rooms

import Client
import components.AppStateContext
import components.redesign.DragIndicatorIcon
import components.redesign.GroupsIcon
import components.redesign.TimelineIcon
import components.redesign.basic.Stack
import components.redesign.forms.Button
import components.redesign.questions.dialog.EditQuestionDialog
import components.redesign.questions.dialog.QuestionPreset
import components.showError
import csstype.*
import dndkit.applyListeners
import dndkit.core.*
import dndkit.modifiers.restrictToVerticalAxis
import dndkit.modifiers.restrictToWindowEdges
import dndkit.sortable.*
import dndkit.utilities.closestCenter
import emotion.css.ClassName
import emotion.react.css
import hooks.useEditDialog
import hooks.useWebSocket
import kotlinx.js.jso
import payloads.requests.ReorderQuestions
import payloads.responses.WSError
import react.*
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import rooms.Room
import rooms.RoomPermission
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.havePermission
import utils.runCoroutine

external interface QuestionManagementProps : Props {
    var room: Room
    var questions: List<Question>
}


val QuestionManagement = FC<QuestionManagementProps> { props ->
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

    val editQuestionOpen = useEditDialog(EditQuestionDialog, jso { this.preset = QuestionPreset.NONE })

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

    RoomHeader {
        +"Quick and condensed moderator overview of questions in the room."
        fullWidth = true
        innerClass = ClassName {
            justifyContent = JustifyContent.center
        }
    }

    if (groupPredsWS is WSError) {
        div { // TODO styling
            css { color = NamedColor.red }
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
                val newOrder = moved.map { Ref<Question>(it) }.toList()
                val reorder = ReorderQuestions(newOrder)
                runCoroutine {
                    Client.sendData("${room.urlPrefix}/questions/reorder", reorder, onError = { showError(it) }) {}
                }
            }
        }
        Stack {
            // apparently, this is the only way? (https://stackoverflow.com/questions/4757844/css-table-column-autowidth)
            fun PropsWithClassName.autoSized() = css { width = 1.px; whiteSpace = WhiteSpace.nowrap }
            fun ChildrenBuilder.autoSizedCol() = ReactHTML.col { autoSized() }
            css {
                padding = 15.px
                alignItems = AlignItems.stretch
            }
            table {
                css(ClassName("qmgmt-tab")) {
                    borderCollapse = BorderCollapse.separate
                    borderSpacing = "0 10px".unsafeCast<BorderSpacing>()
                    "tbody td" {
                        border = None.none
                        backgroundColor = NamedColor.white
                        paddingLeft = 5.px
                        paddingRight = 5.px
                    }
                    "tbody td:first-child" {
                        borderTopLeftRadius = 5.px
                        borderBottomLeftRadius = 5.px
                    }
                    "tbody td:last-child" {
                        borderTopRightRadius = 5.px
                        borderBottomRightRadius = 5.px
                    }
                }
                ReactHTML.colgroup {
                    autoSizedCol()
                    ReactHTML.col {}
                    repeat(3) {
                        autoSizedCol()
                    }
                }
                thead {
                    css {
                        fontWeight = FontWeight.bold
                        color = Color("#333")
                        fontSize = 80.pct
                    }
                    tr {
                        td {}
                        td {  +"Question" }
                        td {
                            autoSized()
                            ReactHTML.abbr {
                                title = "Number of people predicting, total number of prediction updates"
                                    css {
                                        whiteSpace = WhiteSpace.nowrap
                                        textDecoration = None.none
                                        borderBottom = Border(1.px, LineStyle.dotted)
                                    }
                                    GroupsIcon { size = 16 }
                                    +" / "
                                    TimelineIcon { size = 16 }
                            }
                        }
                        if (showGroupPredCol) td { autoSized(); +"Group pred." }
                        if (showResolutionCol) td { autoSized(); +"Resolution" }
                    }
                }
                SortableContext {
                    items = questionOrderReversed
                    strategy = verticalListSortingStrategy
                    tbody {
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
            if (room.havePermission(RoomPermission.ADD_QUESTION)) {
                Button {
                    this.disabled = stale
                    onClick = { editQuestionOpen(null) }
                    +"Add question…"
                }
            }
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
        css {
            verticalAlign = VerticalAlign.middle
            cursor = if (props.isDragging) Cursor.grabbing else Cursor.grab
        }
        color = "#666"
        applyListeners(props.listeners)
    }
}

val QuestionRow = FC<QuestionRowProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    val question = props.question

    val sortable = useSortable(jso<UseSortableArguments> {
        this.id = props.question.id
    })


    tr {
        this.asDynamic().ref = sortable.setNodeRef
        css(ClassName("qmgmt-row")) {
            sortable.transform?.let {transform->
                this.asDynamic().transform = translate(transform.x.px, transform.y.px)//CSS.transform.toString(transform)
            }
            // XXX this was causing confusing visual artifacts, as seen here:
            // https://chat.confido.institute/file-upload/ikuaaPABuHgNjD7XR/reorder-questions-2022-12-06_23.04.39.webm
            //this.asDynamic().transition = sortable.transition
        }
        // TODO: apply sortable.attributes (a11y)

        // apparently, this is the only way? (https://stackoverflow.com/questions/4757844/css-table-column-autowidth)
        fun PropsWithClassName.autoSized() = css { width = 1.px; whiteSpace = WhiteSpace.nowrap }
        td {
            css {
                paddingLeft = 1.px
                paddingRight = 0.px
            }
            autoSized()
            if (!stale)
                DragHandle {
                    // TODO apply sortable.setActivatorNodeRef (requires forwardref in DragHandle)
                    listeners = sortable.listeners
                    isDragging = sortable.isDragging
                }
        }
        td { +question.name }
        td {
            autoSized()
            +"${question.numPredictors} "
            +" / "
            +"${question.numPredictions}"
        }
        if (props.showGroupPredCol)
            td {
                autoSized()
                props.groupPred?.dist?.let { groupDist ->
                    +groupDist.description
                }
            }
        if (props.showResolutionCol)
            td {
                autoSized()
                question.resolution?.let {
                    +it.format()
                }
            }
    }
}
