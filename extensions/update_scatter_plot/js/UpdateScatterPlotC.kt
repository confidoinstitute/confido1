package extensions.update_scatter_plot

import components.AppStateContext
import components.presenter.PresenterPageProps
import components.presenter.presenterPageMap
import components.redesign.BinIcon
import components.redesign.PresenterIcon
import components.redesign.basic.BaseDialogProps
import components.redesign.basic.Dialog
import components.redesign.basic.DialogMenuItem
import components.redesign.basic.Stack
import components.redesign.basic.dialogStateWrapper
import components.redesign.basic.rowStyleTableCSS
import components.redesign.basic.showError
import components.redesign.basic.sansSerif
import components.redesign.forms.*
import components.redesign.presentation.FieldLabel
import components.redesign.presenter.PresenterContext
import components.redesign.questions.dialog.QuestionQuickSettingsDialogProps
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.redesign.rooms.RoomContext
import web.cssom.*
import emotion.react.css
import extensions.reference_forecast.ReferenceForcastKey
import hooks.useCoroutineLock
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.*
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.datalist
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.router.useNavigate
import rooms.Room
import rooms.RoomPermission
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.extensions.get
import tools.confido.extensions.with
import tools.confido.question.GroupTerminology
import tools.confido.question.Prediction
import tools.confido.question.PredictionTerminology
import tools.confido.question.Question
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.refs.Ref
import tools.confido.spaces.BinarySpace
import tools.confido.utils.List2
import tools.confido.utils.capFirst
import tools.confido.utils.formatPercent
import tools.confido.utils.unixNow
import utils.questionUrl
import web.autofill.AutoFill
import web.html.InputType
import web.html.text
import utils.roomUrl
import web.html.ButtonType
import web.html.submit
import web.prompts.confirm

// ============================================================
//  QuestionSelector (existing, for reference question config)
// ============================================================

external interface QuestionSelectorProps : Props {
    var value: String?
    var onChange: ((Ref<Question>?) -> Unit)?
    var disabled: Boolean?
}

val QID_RE = Regex("\\[([a-zA-Z0-9_-]+)\\]\$")
val QuestionSelector = FC<QuestionSelectorProps>("QuestionSelector") { props ->
    val (appState, _) = use(AppStateContext)!!
    val datalistId = useId()
    fun fmtq(q: Question) = "${q.name} [${q.id}]"
    var internalVal by useState(props.value?.let { appState.questions[it] }?.let {fmtq(it)} ?: "")

    div {
        css { width = 100.pct }
        input {
            css { width = 100.pct; padding = 8.px; fontSize = 16.px }
            type = InputType.text
            placeholder = "Search for a question..."
            list = datalistId.toString()
            value = internalVal
            disabled = props.disabled == true
            autoComplete = "off" as AutoFill
            onChange = { event ->
                val newVal = event.target.value
                internalVal = newVal
                val m = QID_RE.find(newVal)
                if (m!=null) props.onChange?.invoke(Ref(m.groups[1]!!.value))
                else props.onChange?.invoke(null)
            }
        }
        datalist {
            id = datalistId
            appState.questions.values.filter{it.answerSpace == BinarySpace }.forEach { question ->
                option { value = fmtq(question); +fmtq(question) }
            }
        }
    }
}

// ============================================================
//  TimepointSelector - unified timepoint picker
// ============================================================

external interface TimepointSelectorProps : Props {
    var title: String
    var value: TimepointReference
    var onChange: (TimepointReference) -> Unit
    /** Existing snapshots (from question + room) for the dropdown. */
    var snapshots: List<Pair<Snapshot, SnapshotScope>>
    /** The question these snapshots belong to (needed for question-scoped snapshot references). */
    var question: Ref<Question>?
    /** If true, allow typing a free-text snapshot name (for flow editor). */
    var allowFreeText: Boolean?
    var timezone: TimeZone
    var defaultDate: LocalDate
}

val TimepointSelector = FC<TimepointSelectorProps> { props ->
    val hasSnapshots = props.snapshots.isNotEmpty()
    val allowFreeText = props.allowFreeText ?: false
    val existingSnapNames = props.snapshots.map { it.first.title }.toSet()

    // Derive the current dropdown key from the value
    val selectedOption = when (val v = props.value) {
        is NowTimepoint -> "now"
        is TimestampTimepoint -> "custom"
        is SnapshotTimepoint -> {
            // If it matches an existing snapshot, select it directly;
            // otherwise use "freetype" (if allowed) so the text input shows
            if (v.name in existingSnapNames) "snap:${v.name}"
            else if (allowFreeText) "freetype"
            else "now"
        }
    }

    FormField {
        title = props.title

        // Main selector dropdown
        ReactHTML.select {
            css { width = 100.pct; padding = 8.px; fontSize = 16.px; marginBottom = 8.px }
            value = selectedOption
            onChange = { event ->
                val v = event.target.value
                when {
                    v == "now" -> props.onChange(NowTimepoint)
                    v == "custom" -> props.onChange(TimestampTimepoint(Clock.System.now()))
                    v == "freetype" -> props.onChange(SnapshotTimepoint(
                        (props.value as? SnapshotTimepoint)?.name ?: "",
                        (props.value as? SnapshotTimepoint)?.question,
                    ))
                    v.startsWith("snap:") -> {
                        val snapName = v.removePrefix("snap:")
                        // Find the snapshot to determine its scope
                        val snap = props.snapshots.find { it.first.title == snapName }
                        if (snap != null) {
                            val questionRef = if (snap.second == SnapshotScope.QUESTION)
                                props.question else null
                            props.onChange(SnapshotTimepoint(snapName, questionRef))
                        } else {
                            props.onChange(SnapshotTimepoint(snapName))
                        }
                    }
                }
            }

            option { value = "now"; +"Now (latest predictions)" }

            if (hasSnapshots) {
                props.snapshots.forEach { (snap, scope) ->
                    option {
                        value = "snap:${snap.title}"
                        val ldt = Instant.fromEpochSeconds(snap.ts.toLong()).toLocalDateTime(props.timezone)
                        val scopeLabel = if (props.snapshots.any { it.second != scope })
                            " [${scope.name.lowercase()}]" else ""
                        +"${snap.title} - ${ldt}$scopeLabel"
                    }
                }
            }

            if (allowFreeText) {
                option { value = "freetype"; +"Snapshot by name..." }
            }

            option { value = "custom"; +"Custom time..." }
        }

        // Free-text snapshot name input (shown when "Snapshot by name..." is selected)
        if (allowFreeText && props.value is SnapshotTimepoint && selectedOption == "freetype") {
            val snapTp = props.value as SnapshotTimepoint
            TextInput {
                value = snapTp.name
                onChange = { e -> props.onChange(SnapshotTimepoint(e.target.value, snapTp.question)) }
                placeholder = "Snapshot name"
                css { width = 100.pct }
            }
            // Scope selector for the snapshot reference
            ReactHTML.select {
                css { width = 100.pct; padding = 6.px; fontSize = 14.px; marginTop = 4.px }
                value = if (snapTp.question != null) "question" else "room"
                onChange = { e ->
                    when (e.target.value) {
                        "room" -> props.onChange(SnapshotTimepoint(snapTp.name, null))
                        "question" -> props.onChange(SnapshotTimepoint(snapTp.name, props.question))
                    }
                }
                option { value = "room"; +"Room-wide snapshot" }
                if (props.question != null) {
                    option { value = "question"; +"Question-specific snapshot" }
                }
            }
        }

        // Custom time input
        if (props.value is TimestampTimepoint) {
            val tsTp = props.value as TimestampTimepoint
            DateTimeInput {
                value = tsTp.time.toLocalDateTime(props.timezone)
                defaultDate = props.defaultDate
                onChange = { newVal, _ ->
                    newVal?.toInstant(props.timezone)?.let { props.onChange(TimestampTimepoint(it)) }
                }
            }
        }
    }
}

// ============================================================
//  Helper: fetch update plot data via POST
// ============================================================

private suspend fun fetchUpdatePlotData(
    questionId: String,
    tp1: TimepointReference,
    tp2: TimepointReference,
    referenceQuestion: Ref<Question>? = null,
): List<List2<Prediction?>> {
    val req = UpdatePlotRequest(
        question = Ref(questionId),
        timepoint1 = tp1,
        timepoint2 = tp2,
        referenceQuestion = referenceQuestion,
    )
    return Client.sendDataRequest("/api${questionUrl(questionId)}/update_plot", req).body()
}

// ============================================================
//  UpdatePlotConfigDialog (question page)
// ============================================================

external interface UpdatePlotConfigDialogProps : BaseDialogProps {
    var question: Question
}

val UpdatePlotConfigDialog = dialogStateWrapper(FC<UpdatePlotConfigDialogProps> { props ->
    var tp1 by useState<TimepointReference>(NowTimepoint)
    var tp2 by useState<TimepointReference>(NowTimepoint)
    var flipped by useState(false)
    val presenterCtl = use(PresenterContext)!!
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val q = props.question
    val room = q.room

    // Get available snapshots
    val questionSnapshots = q.extensionData[SnapshotsKey] ?: emptyList()
    val roomSnapshots = room?.extensionData?.get(SnapshotsKey) ?: emptyList()
    val allSnapshots = (questionSnapshots.map { it to SnapshotScope.QUESTION } +
                        roomSnapshots.map { it to SnapshotScope.ROOM }).sortedBy { it.first.ts }

    Dialog {
        +props
        title = "Plot updates"
        Form {
            FormSection {
                TimepointSelector {
                    title = "Before time (x axis)"
                    value = tp1
                    onChange = { tp1 = it }
                    snapshots = allSnapshots
                    question = q.ref
                    allowFreeText = false
                    timezone = tz
                    defaultDate = today
                }

                TimepointSelector {
                    title = "After time (y axis)"
                    value = tp2
                    onChange = { tp2 = it }
                    snapshots = allSnapshots
                    question = q.ref
                    allowFreeText = false
                    timezone = tz
                    defaultDate = today
                }
            }
        }

        val data = useSuspendResult(props.open, tp1, tp2, q.id) {
            if (!props.open) return@useSuspendResult null
            fetchUpdatePlotData(q.id, tp1, tp2)
        }?.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>?>()

        UpdateSlopeChart {
            height = 300.0
            this.preds = data ?: emptyList()
            this.referenceForecast = q.extensionData?.get(ReferenceForcastKey)
            this.flipped = flipped
        }
        div {
            css {
                display = Display.flex
                justifyContent = JustifyContent.spaceBetween
                alignItems = AlignItems.center
                marginTop = 8.px
            }
            FormSwitch {
                label = "Flip axes"
                comment = "Probability horizontal, time vertical"
                checked = flipped
                onChange = { flipped = it.target.checked }
            }
            IconButton {
                PresenterIcon {}
                onClick = {
                    presenterCtl.offer(UpdatePlotPV(q.ref, tp1, tp2))
                }
            }
        }
    }
})

// ============================================================
//  Snapshot Manager Dialog (unchanged)
// ============================================================

external interface SnapshotManagerDialogProps : BaseDialogProps {
    var room: Room?
    var question: Question?
}

val SnapshotManagerDialog = FC<SnapshotManagerDialogProps> { props->
    val question = props.question
    val room = props.room ?: props.question?.room
    val questionSnapshots = question?.extensionData[SnapshotsKey] ?: emptyList()
    val roomSnapshots = room?.extensionData[SnapshotsKey] ?: emptyList()
    val snapshots = (questionSnapshots.map { it to SnapshotScope.QUESTION } +
                     roomSnapshots.map { it to SnapshotScope.ROOM }).sortedBy { it.first.ts }
    val managerScope = if (question == null) SnapshotScope.ROOM else SnapshotScope.QUESTION
    val scopeName = managerScope.name.lowercase()
    val multiScope = snapshots.any { it.second != managerScope }
    var newSnapshotName by useState("")
    var newSnapshotTime by useState<Instant>()
    var newPublic by useState(false)
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val (appState,stale) = use(AppStateContext)!!
    val submitLock = useCoroutineLock()
    if (room == null) return@FC
    Dialog {
        title = "${scopeName.capFirst()} snapshots"
        +props
        FormSection {
            title = "Current snaphots"
            if (snapshots.isEmpty()) {
                i { +"There are currently no snapshots for this $scopeName." }
            } else {
                table {
                    className = rowStyleTableCSS
                    thead {
                        th { +"Timestamp" }
                        th { +"Name" }
                        if (multiScope) th { +"Scope" }
                        th {} // actions
                    }
                    tbody {
                        snapshots.forEach { (snap, scope) ->
                            tr {
                                td {
                                    val ldt = Instant.fromEpochSeconds(snap.ts.toLong(), 0).toLocalDateTime(TimeZone.currentSystemDefault())
                                    +ldt.toString()
                                }
                                td { +snap.title }
                                if (multiScope) td { +scope.name.lowercase().capFirst() }
                                td {
                                    IconButton {
                                        BinIcon { size = 18 }
                                        onClick = {
                                            if (confirm("Delete the snapshot '${snap.title}'?")) {
                                                submitLock {
                                                    if (question != null && scope == SnapshotScope.QUESTION)
                                                        Client.httpClient.delete("/api${questionUrl(question.id)}/snapshots/${snap.id}")
                                                    else
                                                        Client.httpClient.delete("/api${roomUrl(room.id)}/snapshots/${snap.id}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Form {
            onSubmit = {
                it.preventDefault()
                submitLock {
                    val url = if (question != null) "/api${questionUrl(question.id)}/snapshots/add"
                              else "/api${roomUrl(room.id)}/snapshots/add"
                    val newSnap = Snapshot(ts = newSnapshotTime?.epochSeconds?.toInt() ?: unixNow(), title=newSnapshotName, public=newPublic, owner=null)
                    Client.sendData(url, newSnap, onError = { showError(it) }) {
                        newSnapshotName = ""
                        newSnapshotTime = null
                        newPublic = false
                    }
                }
            }
            FormSection {
                title = "Create new snapshot"
                InputFormField<LocalDateTime, DateTimeInputProps>()() {
                    title = "Snapshot time"
                    inputComponent = DateTimeInput
                    inputProps = jso {
                        value = newSnapshotTime?.toLocalDateTime(tz)
                        defaultDate = today
                        onChange = { newVal, err ->
                            newSnapshotTime = newVal?.toInstant(tz)
                        }
                        placeholder = "(now)"
                    }
                }
                FormField {
                    title = "Snapshot name"
                    TextInput {
                        value = newSnapshotName
                        onChange  = { newSnapshotName = it.target.value }
                    }
                }
                if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))
                FormSwitch {
                    label = "Public"
                    comment = "Public snapshots are visible to all users in this room. Private snapshots are only visible to you."
                    checked = newPublic
                    onChange = { newPublic = it.target.checked }
                }
                Button {
                    type = ButtonType.submit
                    +"Create snapshot"
                }
            }
        }
    }
}

// ============================================================
//  UpdatePlot Presenter Page
// ============================================================

external interface UpdatePlotProps : Props {
    var preds: List<List<Prediction?>>
    var referenceForecast: Double?
    var timepointLabels: List<String?>?
}
val UpdatePlotPP = FC<PresenterPageProps<UpdatePlotPV>> { props ->
    val view = props.view
    val q = view.question.deref()
    val referenceForecast = q?.extensionData?.get(ReferenceForcastKey)
    val rawData = useSuspendResult(view.timepoint1, view.timepoint2, view.question.id) {
        fetchUpdatePlotData(view.question.id, view.timepoint1, view.timepoint2)
    }
    val data = rawData?.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>?>()
    var flipped by useState(false)
    val viewport = hooks.useViewportSize()
    val availableHeight = (viewport.height * 0.88).coerceAtLeast(200.0)
    val availableWidth = viewport.width.toDouble()
    val chartWidth = if (flipped) availableWidth
                     else availableWidth.coerceAtMost(availableHeight * 0.5)
    val chartHeight = if (flipped) availableHeight.coerceAtMost(availableWidth * 0.4).coerceAtMost(500.0)
                      else availableHeight

    div {
        css {
            position = Position.relative
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
            maxWidth = 100.vw
            width = 100.vw
            maxHeight = 100.vh
            height = 100.vh
            overflow = Overflow.hidden
        }
        q?.name?.let {
            div {
                css {
                    flexShrink = number(0.0)
                    fontSize = 2.5.vh
                    fontWeight = FontWeight.bold
                    padding = Padding(1.vh, 2.vw)
                    textAlign = TextAlign.center
                    maxWidth = 90.vw
                    this.overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                }
                +it
            }
        }
        if (rawData != null)
        div {
            css {
                flexGrow = number(1.0)
                flexShrink = number(1.0)
                minHeight = 0.px
                this.overflow = Overflow.hidden
                display = Display.flex
                justifyContent = JustifyContent.center
            }
            UpdateSlopeChart {
                preds = rawData
                this.flipped = flipped
                height = chartHeight
                width = chartWidth
            }
        }
        if (rawData != null)
        Stack {
            direction = FlexDirection.row
            css {
                flexGrow = number(0.0)
                flexShrink = number(0.0)
                fontSize = 2.5.vh
                justifyContent = JustifyContent.spaceEvenly
                padding = Padding(0.5.vh, 2.vw)
                alignSelf = AlignSelf.stretch
            }
            listOf(0,1).map { which->
                val gp = rawData.mapNotNull { (it[which]?.dist as? BinaryDistribution)?.yesProb }.average()
                if (gp.isNaN()) return@map
                div {
                    val adverb = listOf("before", "after")[which]
                    +(q?.groupTerminology ?: GroupTerminology.GROUP).term.capFirst()
                    +" ${(q?.predictionTerminology ?: PredictionTerminology.PREDICTION).term}"
                    +" $adverb: ${formatPercent(gp)}"
                }
            }
        }
        div {
            css {
                position = Position.absolute
                bottom = 8.px
                right = 8.px
                backgroundColor = Color("rgba(255,255,255,0.7)")
                borderRadius = 4.px
                padding = Padding(4.px, 8.px)
                fontSize = 12.px
                cursor = Cursor.pointer
                userSelect = None.none
                color = Color("#64748b")
                hover { backgroundColor = Color("rgba(255,255,255,0.9)") }
            }
            onClick = { flipped = !flipped }
            +(if (flipped) "\u21C5 Normal" else "\u21C4 Flip axes")
        }
    }
}

// ============================================================
//  Presentation Flow Editors
// ============================================================

/** PV editor for UpdatePlotPV with timepoint selectors. */
private val UpdatePlotPVEditorComponent = FC<tools.confido.extensions.PresenterViewEditorProps> { props ->
    val view = props.view as? UpdatePlotPV ?: return@FC
    val (appState, _) = use(AppStateContext)!!
    val room = use(components.redesign.rooms.RoomContext)!!
    val questions = appState.questions.values.filter { q -> room.questions.any { it.id == q.id } }.sortedBy { it.name }
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = view.question.id
        onChange = { props.onChange(view.copy(question = Ref(it))) }
    }
    TimepointSelector {
        title = "Before (x axis)"
        value = view.timepoint1
        onChange = { props.onChange(view.copy(timepoint1 = it)) }
        snapshots = emptyList() // no existing snapshots in flow editor context
        question = view.question
        allowFreeText = true
        timezone = tz
        defaultDate = today
    }
    TimepointSelector {
        title = "After (y axis)"
        value = view.timepoint2
        onChange = { props.onChange(view.copy(timepoint2 = it)) }
        snapshots = emptyList()
        question = view.question
        allowFreeText = true
        timezone = tz
        defaultDate = today
    }
}

/** Flow action editor for CreateSnapshotAction. */
private val CreateSnapshotActionEditor = FC<tools.confido.extensions.FlowActionEditorProps> { props ->
    val action = props.action as? CreateSnapshotAction ?: return@FC
    val (appState, _) = use(AppStateContext)!!
    val room = use(components.redesign.rooms.RoomContext)!!
    val questions = appState.questions.values.filter { q -> room.questions.any { it.id == q.id } }.sortedBy { it.name }

    FieldLabel { +"Snapshot name" }
    TextInput {
        value = action.name
        onChange = { e -> props.onChange(action.copy(name = e.target.value)) }
        placeholder = "e.g. Before discussion"
        css { width = 100.pct }
    }

    FieldLabel { +"Scope" }
    Select {
        value = if (action.question != null) "question" else "room"
        onChange = { e ->
            when (e.target.value) {
                "room" -> props.onChange(action.copy(question = null))
                "question" -> props.onChange(action.copy(question = Ref(questions.firstOrNull()?.id ?: "")))
            }
        }
        css { width = 100.pct }
        option { value = "room"; +"Room-wide" }
        option { value = "question"; +"Specific question" }
    }

    if (action.question != null) {
        FieldLabel { +"Question" }
        QuestionPicker {
            this.questions = questions
            selectedId = action.question.id
            onChange = { props.onChange(action.copy(question = Ref(it))) }
        }
    }
}

// ============================================================
//  Extension Registration
// ============================================================

object UpdateScatterPlotCE: ClientExtension, UpdateScatterPlotExt {
    override fun editQuestionDialogExtra(props: EditQuestionDialogProps, cb: ChildrenBuilder) {
        var referenceQuestionId by useContextState<Ref<Question>?>(
            ExtensionContextPlace.EDIT_QUESTION_DIALOG,
            "reference_question",
            props.entity?.extensionData?.get(UpdateReferenceQuestionKey)
        )

        cb.apply {
            FormSection {
                title = "Reference Question"
                FormField {
                    title = "Reference question for updates"
                    comment = "Select a question to compare updates against"
                    QuestionSelector {
                        value = referenceQuestionId?.id
                        onChange = { newId -> referenceQuestionId = newId }
                        disabled = false
                    }
                }
            }
        }
    }

    override fun assembleQuestion(q: Question, states: Map<String, dynamic>): Question {
        val refQuestionId = states["reference_question"] as? String
        return q.copy(extensionData = q.extensionData.with(UpdateReferenceQuestionKey,
            refQuestionId?.let { id -> Ref<Question>(id) }
        ))
    }

    override fun questionQuickSettingsExtra(props: QuestionQuickSettingsDialogProps, cb: ChildrenBuilder, onClose: ()->Unit) {
        var updatePlotState  by useContextState(ExtensionContextPlace.QUESTION_PAGE, "state", default = UpdatePlotState.NONE)
        val room = use(RoomContext)!!
        val (appState,_) = use(AppStateContext)!!
        if (appState.hasPermission(room, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS))
        cb.apply {
            DialogMenuItem {
                text = "Update plot"
                onClick = { updatePlotState = UpdatePlotState.CONFIG_OPEN; onClose() }
            }
            DialogMenuItem {
                text = "Manage snapshots"
                onClick = { updatePlotState = UpdatePlotState.SNAPSHOT_MANAGER_OPEN; onClose() }
            }
        }
    }

    enum class UpdatePlotState {
        NONE, CONFIG_OPEN, SNAPSHOT_MANAGER_OPEN,
    }

    override fun questionPageExtra(q: Question, place: ClientExtension.QuestionPagePlace, cb: ChildrenBuilder) {
        when (place) {
            ClientExtension.QuestionPagePlace.QUESTION_PAGE_END -> {
                var state  by useContextState(ExtensionContextPlace.QUESTION_PAGE, "state", default = UpdatePlotState.NONE)
                val presenterCtl = use(PresenterContext)!!
                val nav = useNavigate()
                val room = use(RoomContext)!!
                cb.apply {
                    SnapshotManagerDialog {
                        open = (state == UpdatePlotState.SNAPSHOT_MANAGER_OPEN)
                        onClose = { state = UpdatePlotState.NONE }
                        this.room = room
                        this.question = q
                    }
                    UpdatePlotConfigDialog {
                        open = (state == UpdatePlotState.CONFIG_OPEN)
                        onClose = { state = UpdatePlotState.NONE }
                        question = q
                    }
                }
            }
        }
    }

    override fun registerPresenterPages() =
        mapOf(presenterPageMap(UpdatePlotPP))

    override fun registerPresenterViewTypes() = listOf(
        tools.confido.extensions.PresenterViewTypeEntry(
            key = "updatePlot",
            label = "Update scatter plot",
            createDefault = { UpdatePlotPV(Ref(""), NowTimepoint, NowTimepoint) },
            editor = UpdatePlotPVEditorComponent,
            matches = { it is UpdatePlotPV },
        )
    )

    override fun registerFlowActionTypes() = listOf(
        tools.confido.extensions.FlowActionTypeEntry(
            key = "createSnapshot",
            label = "Create snapshot",
            createDefault = { CreateSnapshotAction("") },
            editor = CreateSnapshotActionEditor,
            matches = { it is CreateSnapshotAction },
        )
    )
}
