package components.redesign.rooms

import Client
import components.AppStateContext
import components.redesign.*
import components.redesign.basic.*
import components.redesign.forms.Button
import components.redesign.forms.ButtonUnstyled
import components.redesign.forms.Checkbox
import components.redesign.forms.IconButton
import components.redesign.forms.IconLink
import components.redesign.questions.ChipCSS
import components.redesign.questions.StatusChip
import components.redesign.questions.dialog.AddQuestionPresetDialog
import components.redesign.questions.dialog.EditQuestionDialog
import components.redesign.questions.dialog.QuestionPreset
import components.redesign.questions.dialog.QuestionResolveDialog
import web.cssom.*
import dndkit.applyListeners
import dndkit.core.*
import dndkit.modifiers.restrictToVerticalAxis
import dndkit.modifiers.restrictToWindowEdges
import dndkit.sortable.*
import dndkit.utilities.closestCenter
import web.html.HTMLElement
import csstype.PropertiesBuilder
import emotion.css.ClassName
import emotion.react.css
import hooks.useCoroutineLock
import hooks.useEditDialog
import hooks.useWebSocket
import js.objects.unsafeJso as jso
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionClearAnswers
import payloads.requests.EditQuestionMulti
import payloads.requests.EditQuestionState
import payloads.requests.ReorderQuestions
import payloads.responses.WSError
import react.*
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.ReactHTML.ul
import react.router.dom.Link
import rooms.Room
import rooms.RoomPermission
import tools.confido.extensions.ClientExtension
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.havePermission
import tools.confido.utils.capFirst
import tools.confido.utils.toInt
import utils.except
import utils.runCoroutine
import web.cssom.FlexDirection.Companion.row
import web.cssom.FontStyle.Companion.italic
import web.cssom.Position.Companion.sticky
import web.cssom.TextOverflow.Companion.ellipsis
import web.window.WindowTarget
import web.window._blank

external interface QuestionManagementProps : Props {
    var room: Room
    var questions: List<Question>
}

private val QMHelpDialog = FC<DialogProps> { props->
    Dialog {
        title = "Help"
        action = "Close"
        onAction = { props.onClose?.invoke() }
        +props
        ul {
            li {
                + "You can drag the "
                DragIndicatorIcon{
                    size = 20
                    css {
                        verticalAlign = VerticalAlign.middle
                        margin = -3.px
                    }
                }
                +" handles to change question order. "
                +" This determines the order in which questions are shown to forecasters. "
                +" By default, newest questions are put on top."
            }
            li {
                +"You can click the "
                SettingsIcon{
                    css { verticalAlign = VerticalAlign.middle }
                }
                +" icon to edit the question (open the question edit dialog)."
            }
            li {
                +"You can click the question state indicator (e.g. "
                StatusChip {
                    color = QuestionState.OPEN.palette.color
                    text = "Open"
                }
                +") to quickly change question state."
            }
            li {
                +"You can click the question text to open the question page (in a new tab)."
            }
            li {
                +"The "
                GroupsIcon {
                    css { verticalAlign = VerticalAlign.middle }
                    size = 16
                }
                +" / "
                TimelineIcon {
                    css { verticalAlign = VerticalAlign.middle }
                    size = 16
                }
                +" column shows the number of people who have predicted on a given question and the total number of predictions made (incl. updates)."
            }
            li {
                +"You can click in the Resolution column to quickly add/edit question resolution."
            }
        }
    }
}

val QuestionManagement = FC<QuestionManagementProps> { props ->
    val (appState, stale) = use(AppStateContext)!!
    val room = props.room

    val groupPredsWS = useWebSocket<Map<String, Prediction?>>("/state${room.urlPrefix}/group_pred")
    val groupPreds = groupPredsWS.data ?: emptyMap()

    val mouseSensor = useSensor<MouseSensorOptions>(MouseSensor)
    val sensors = useSensors(mouseSensor)
    val submitLock = useCoroutineLock()

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

    var preset by useState(QuestionPreset.NONE)
    var presetOpen by useState(false)
    var helpOpen by useState(false)
    var selectedRows by useState<Set<Ref<Question>>>(emptySet())
    fun selectedQuestions() = props.questions.filter { it.ref in selectedRows }

    val editQuestionOpen = useEditDialog(EditQuestionDialog, jso {
        this.preset = preset
    })

    fun addQuestion() {
        presetOpen = true
    }

    AddQuestionPresetDialog {
        open = presetOpen
        onClose = {presetOpen = false}
        onPreset = {preset = it; presetOpen = false; editQuestionOpen(null)}
    }

    QMHelpDialog {
        open = helpOpen
        onClose = { helpOpen = false }
    }

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
        Stack {
            direction = FlexDirection.row
            css {
                flexGrow = number(1.0)
                alignItems = AlignItems.center
                justifyItems = JustifyItems.center
                marginLeft = 5.px
                marginRight = 5.px
                gap = 0.px
                fontSize = 14.px
                fontWeight = integer(500)
                color = Color("#888")
            }
            div { +"Quick and condensed moderator overview of questions in the room." }
            IconButton {
                HelpIcon{}
                onClick = { helpOpen = true }
            }

            if (appState.hasPermission(room, RoomPermission.ADD_QUESTION)) {
                div { css {flexGrow = number(1.0)} }
                RoomHeaderButton {
                    +"Create a question"
                    onClick = { addQuestion() }
                }
            }
        }
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
                css(ClassName("qmgmt-tab"), rowStyleTableCSS) {
                }
                ReactHTML.colgroup {
                    autoSizedCol()
                    autoSizedCol()
                    autoSizedCol()
                    autoSizedCol()
                    ReactHTML.col {}
                    repeat(3) {
                        autoSizedCol()
                    }
                }
                thead {
                    tr {
                        th {}
                        th {}

                        th {
                            Stack {
                                direction = FlexDirection.row
                                css {
                                    alignItems = AlignItems.center
                                }
                                Checkbox {
                                    sz = 20.0
                                    checked = selectedQuestions().size == props.questions.size
                                    onChange = { ev->
                                        if (ev.target.checked) {
                                            selectedRows = props.questions.map {it.ref}.toSet()
                                        } else {
                                            selectedRows = emptySet()
                                        }
                                    }
                                }
                            }
                        }
                        th { +"State" }
                        th { +"Question" }
                        th {
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
                        if (showGroupPredCol) th { autoSized(); +"Group pred." }
                        if (showResolutionCol) th { autoSized(); +"Resolution" }
                    }
                }
                tbody {
                    SortableContext {
                        items = questionOrderReversed
                        strategy = verticalListSortingStrategy
                        questionOrderReversed.map { questionId ->
                            props.questions.find { it.id == questionId }?.let { question ->
                                QuestionRow {
                                    key = question.id
                                    this.checked = question.ref in selectedRows
                                    this.onCheck = { checked ->
                                        if (checked) selectedRows += question.ref
                                        else selectedRows -= question.ref
                                    }
                                    this.question = question
                                    this.room = room
                                    this.showGroupPredCol = showGroupPredCol
                                    this.groupPred = groupPreds.get(question.id)
                                    this.showResolutionCol = showResolutionCol
                                    this.onEdit = { preset = QuestionPreset.NONE; editQuestionOpen(question) }
                                }
                            }
                        }
                    }
                    if (!selectedQuestions().isEmpty()) {
                        SelectionActionRow {
                            this.selectedQuestions = selectedQuestions().toSet()
                            onClose = { selectedRows=emptySet() }
                        }
                    }
                }
            }
        }
    }

    ClientExtension.forEach  { it.questionManagementExtra(room, this) }
}


external interface QuestionRowProps : Props {
    var room: Room
    var question: Question
    var showGroupPredCol: Boolean
    var groupPred: Prediction?
    var showResolutionCol: Boolean
    var onEdit: (() -> Unit)?
    var checked: Boolean?
    var onCheck: ((Boolean) -> Unit)?
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

external interface StatusSelectorProps: PropsWithClassName {
    var choices: Set<QuestionState>?
    var exclude: Set<QuestionState>?
    var onSelect: ((QuestionState)->Unit)?
    var onClose: (()->Unit)?
    var arrow: Boolean?
}
val StatusSelector = FC<StatusSelectorProps> { props->
    val choices = QuestionState.entries.filter {
        (props.choices?.contains(it) ?: true)
                && !(props.exclude?.contains(it) ?: false)
    }
    Stack {
        direction = FlexDirection.row
        css {
            gap = 10.px
            alignItems = AlignItems.center
        }

        if (props.arrow ?: false)
        span {
            css {
                fontSize = 16.px
                lineHeight = 16.px
                fontWeight = FontWeight.bold
            }
            +"⭢"
        }
        choices.forEach {newState->
            button {
                css(ChipCSS) {
                    border = Border(1.px, LineStyle.solid, newState.palette.color)
                    color = newState.palette.color
                    backgroundColor = NamedColor.white
                }
                +newState.name.lowercase().capFirst()
                onClick = {
                    props.onSelect?.invoke(newState)
                }
            }
        }
        if (props.onClose != null)
        IconButton {
            CloseIcon{
                css {
                    height = 12.px
                }
            }
            onClick = { props.onClose?.invoke() }
        }
    }
}

val QuestionRow = FC<QuestionRowProps> { props ->
    val (_, stale) = use(AppStateContext)!!
    val question = props.question

    val sortable = useSortable(jso {
        this.id = props.question.id
    })

    var changingState by useState(false)
    var fullHeight by useState(0.0)
    val submitLock = useCoroutineLock()
    var resolveOpen by useState(false)
    val room = props.room

    QuestionResolveDialog {
        this.question = question
        this.open = resolveOpen
        this.onClose = { resolveOpen = false; changingState = false }
    }

    tr {
        this.asDynamic().ref = sortable.setNodeRef
        css(ClassName("qmgmt-row")) {
            sortable.transform?.let {transform->
                this.asDynamic().transform = translate(transform.x.px, transform.y.px)//CSS.transform.toString(transform)
            }
            // XXX this was causing confusing visual artifacts, as seen here:
            // https://chat.confido.institute/file-upload/ikuaaPABuHgNjD7XR/reorder-questions-2022-12-06_23.04.39.webm
            //this.asDynamic().transition = sortable.transition
            if (changingState) height = fullHeight.px
        }
        // TODO: apply sortable.attributes (a11y)

        // apparently, this is the only way? (https://stackoverflow.com/questions/4757844/css-table-column-autowidth)
        fun PropertiesBuilder.autoSized() {  width = 1.px; whiteSpace = WhiteSpace.nowrap }
        fun PropsWithClassName.autoSized() = css(className) { autoSized() }
        td {
            css {
                autoSized()
                "&&" {
                    paddingLeft = 1.px
                    paddingRight = 0.px
                }
            }
            if (!stale) {
                DragHandle {
                    // TODO apply sortable.setActivatorNodeRef (requires forwardref in DragHandle)
                    listeners = sortable.listeners
                    isDragging = sortable.isDragging
                }
            }
        }
        td {
            css {
                autoSized()
                verticalAlign = VerticalAlign.middle
                "&&" {
                    paddingLeft = 1.px
                }
            }
            Stack {
                direction = FlexDirection.row
                css {
                    alignItems = AlignItems.center
                    gap = 8.px
                }
                ButtonUnstyled {
                    css {
                        height = Auto.auto
                        lineHeight = 10.px
                    }
                    SettingsIcon {}
                    onClick = { changingState = false; props.onEdit?.invoke() }
                }
            }
        }
        td {
            css {
                autoSized()
            }

            Stack {
                direction = FlexDirection.row
                css {
                    alignItems = AlignItems.center
                }
                Checkbox {
                    sz = 20.0
                    checked = props.checked ?: false
                    onChange = {
                        props.onCheck?.invoke(it.target.checked)
                    }
                }
            }
        }
        val state = question.state
        val palette = state.palette
        td {
            css { verticalAlign = VerticalAlign.middle }
            autoSized()
            //StatusIndicator{
            //    state = question.state
            //}
            button {
                css(ChipCSS) {
                    cursor = Cursor.pointer
                    backgroundColor = palette.color
                    color = NamedColor.white
                }
                + state.name.lowercase().capFirst()// + " " + if (changingState) "▴" else "▾"
                onClick = {
                    if (!changingState) {
                        val row = (it.target as HTMLElement).parentElement?.parentElement
                        println(row)
                        row?.let {
                            fullHeight = row.getBoundingClientRect().height
                            println("fh ${row.getBoundingClientRect().height}")
                        }
                    }
                    changingState = !changingState
                }
            }
        }
        if (changingState) {
            td {
                colSpan = 2 + props.showGroupPredCol.toInt() + props.showResolutionCol.toInt()
                css {
                    verticalAlign = VerticalAlign.middle
                }
                Stack {
                    direction = FlexDirection.row
                    css {
                        gap = 10.px
                        alignItems = AlignItems.center
                    }
                    StatusSelector {
                        arrow = true
                        exclude = setOf(question.state)
                        onSelect = { newState ->
                            if (newState == QuestionState.RESOLVED && question.resolution == null) {
                                resolveOpen = true
                            } else submitLock {
                                val edit: EditQuestion = EditQuestionState(newState)
                                Client.sendData(
                                    "${props.question.urlPrefix}/edit",
                                    edit,
                                    onError = { showError(it); changingState = false }) {
                                    changingState = false
                                }
                            }
                        }
                        onClose = { changingState = false }
                    }
                }
            }
        } else {
            td { Link {
                this.to = props.room.urlPrefix  + question.urlPrefix
                this.target = WindowTarget._blank
                css(LinkUnstyled) {}
                +question.name
            } }
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
                    css {
                        autoSized()
                        ".edit-icon" {
                            if (question.resolution != null) visibility = Visibility.hidden
                        }
                        "&:hover .edit-icon" {
                            visibility = Visibility.visible
                        }
                        if (question.resolution != null) {
                            // The hidden pencil icon can serve instead of padding so that
                            // there is not too much blank space on the right when it is
                            // hidden.
                            "&&" { paddingRight = 1.px }
                        }
                    }


                    ButtonUnstyled {
                        css {
                            height = Auto.auto
                            lineHeight = 10.px
                            flexDirection = FlexDirection.row
                            display = Display.flex
                            alignItems = AlignItems.center
                            gap = 3.px
                        }
                        question.resolution?.let {
                            +it.format()
                        }
                        EditIcon {
                            className = ClassName("edit-icon")
                            size = 14
                            color = "#888"
                        }
                        onClick = { changingState = false; resolveOpen = true; }
                    }
                }
        }
    }
}

external interface SelectionActionMenuProps: DialogMenuProps {
    var selectedQuestions: Set<Question>
    var onActionDone: (()->Unit)?

}

val SelectionActionMenu = FC<SelectionActionMenuProps> { props->
    var clearConfirmOpen by useState(false)
    val submitLock = useCoroutineLock()
    val selectedQuestions = props.selectedQuestions
    ConfirmDialog {
        open = clearConfirmOpen
        onClose = { clearConfirmOpen = false }
        title = "Clear all answers?"
        p { +"This will result in loss of all answers made for the following questions:" }
        ul {
            selectedQuestions.forEach {
                li {
                    css {
                        textOverflow = ellipsis
                        fontStyle = italic
                    }
                    +it.name
                }
            }
        }
        p { +"This action cannot be undone." }
        onConfirm = {
            submitLock {
                Client.sendData(
                    "/questions/multiedit",
                    EditQuestionMulti(
                        selectedQuestions.map { it.ref }.toSet(),
                        EditQuestionClearAnswers
                    ),
                    onError = { showError(it); clearConfirmOpen=false }) {
                    clearConfirmOpen=false
                    props.onActionDone?.invoke()
                }
            }
        }
    }
    DialogMenu {
        +props.except("selectedQuestions")

        DialogMenuItem {
            text = "Clear answers"
            icon = BinIcon
            variant = DialogMenuItemVariant.dangerous
            onClick = {
                props.onClose?.invoke()
                clearConfirmOpen = true
            }
        }
    }
}

external interface SelectionActionRowProps : PropsWithClassName {
    var selectedQuestions: Set<Question>
    var onClose: (()->Unit)?
}

val SelectionActionRow = FC<SelectionActionRowProps> { props->
    val selectedQuestions = props.selectedQuestions
    val submitLock = useCoroutineLock()
    var menuOpen by useState(false)
    SelectionActionMenu {
        open = menuOpen
        onClose = { menuOpen = false }
        this.selectedQuestions = selectedQuestions
        onActionDone = { props.onClose?.invoke() }
    }
    tr {
        css(override = props.className) {
            height = 30.px
            position = sticky
            bottom = 0.px
            "&& td" {
                //backgroundColor = Color("#72cde9")
                backgroundColor = Color("#adecff")
            }
        }
        td {
            colSpan = 2
        }
        td {
        colSpan = 10
        Stack {
            direction = row
            css {
                gap = 10.px
                justifyContent = JustifyContent.spaceBetween
                alignItems = AlignItems.center
            }
            b {
                +"${selectedQuestions.size} question${if (selectedQuestions.size > 1) "s" else ""} selected"
            }
            StatusSelector {
                arrow = true
                onSelect={ newState->
                    submitLock {
                        Client.sendData(
                            "/questions/multiedit",
                            EditQuestionMulti(
                                selectedQuestions.map { it.ref }.toSet(),
                                EditQuestionState(newState)
                            ),
                            onError = { showError(it) }) {  props.onClose?.invoke() }
                    }
                }
            }
            Stack { // right-aligned buttons
                direction = row
                IconButton {
                    NavMenuIcon {}
                    onClick = { menuOpen = true }
                }
                if (props.onClose!=null)
                IconButton {
                    CloseIcon {}
                    onClick = {
                        props.onClose?.invoke()
                    }
                }
            }
        }
        }
    }
}
