package extensions

import components.*
import components.AppStateContext
import components.presenter.PresenterPageProps
import components.presenter.PresenterPageType
import components.presenter.presenterPageMap
import components.redesign.BinIcon
import components.redesign.PresenterIcon
import components.redesign.basic.BaseDialogProps
import components.redesign.basic.Dialog
import components.redesign.basic.DialogMenuItem
import components.redesign.basic.DialogProps
import components.redesign.basic.Stack
import components.redesign.basic.dialogStateWrapper
import components.redesign.basic.rowStyleTableCSS
import components.redesign.basic.showError
import components.redesign.forms.*
import components.redesign.presenter.PresenterContext
import components.redesign.questions.dialog.QuestionQuickSettingsDialogProps
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.redesign.rooms.RoomContext
import web.cssom.*
import emotion.react.css
import extensions.UpdateScatterPlotCE.UpdatePlotState
import hooks.useCoroutineLock
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.path
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
import space.kscience.plotly.layout
import space.kscience.plotly.models.*
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
import tools.confido.state.PresenterView
import tools.confido.state.QuestionPV
import tools.confido.utils.List2
import tools.confido.utils.capFirst
import tools.confido.utils.cross
import tools.confido.utils.formatPercent
import tools.confido.utils.unixNow
import utils.questionUrl
import web.autofill.AutoFill
import web.dom.ElementId
import web.html.InputType
import web.html.text
import utils.roomUrl
import web.html.ButtonType
import web.html.submit
import web.prompts.confirm
import kotlin.math.abs
import kotlin.reflect.KClass

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
        css {
            width = 100.pct
        }
        input {
            css {
                width = 100.pct
                padding = 8.px
                fontSize = 16.px
            }
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
                if (m!=null) {
                    props.onChange?.invoke(Ref(m.groups[1]!!.value))
                } else {
                    props.onChange?.invoke(null)
                }
            }
        }
        datalist {
            id = datalistId
            appState.questions.values.filter{it.answerSpace == BinarySpace }.forEach { question ->
                option {
                    value = fmtq(question)
                    +fmtq(question)
                }
             }
        }
    }
}

external interface SnapshotOrTimeSelectorProps : Props {
    var title: String
    var value: Instant?
    var onChange: (Instant?) -> Unit
    var snapshots: List<Pair<Snapshot, SnapshotScope>>
    var showNowOption: Boolean?
    var timezone: TimeZone
    var defaultDate: LocalDate
    var placeholder: String?
}

val SnapshotOrTimeSelector = FC<SnapshotOrTimeSelectorProps> { props ->
    var selectedOption by useState<String>(if (props.showNowOption == true) "now" else "")
    val hasSnapshots = props.snapshots.isNotEmpty()
    
    FormField {
        title = props.title
        
        if (hasSnapshots) {
            // Snapshot selector dropdown
            ReactHTML.select {
                css {
                    width = 100.pct
                    padding = 8.px
                    fontSize = 16.px
                    marginBottom = 8.px
                }
                value = selectedOption
                onChange = { event ->
                    val value = event.target.value
                    selectedOption = value
                    when (value) {
                        "now" -> props.onChange(null)
                        "custom" -> { /* Keep current value */ }
                        "" -> props.onChange(null)
                        else -> props.onChange(Instant.fromEpochSeconds(value.toLong()))
                    }
                }
                
                if (props.showNowOption == true) {
                    ReactHTML.option {
                        value = "now"
                        +"Now"
                    }
                } else {
                    ReactHTML.option {
                        value = ""
                        +"(Select a snapshot or enter custom time)"
                    }
                }
                
                props.snapshots.forEach { (snap, scope) ->
                    ReactHTML.option {
                        value = snap.ts.toString()
                        val ldt = Instant.fromEpochSeconds(snap.ts.toLong()).toLocalDateTime(props.timezone)
                        val scopeLabel = if (props.snapshots.any { it.second != scope }) 
                                        " [${scope.name.lowercase()}]" else ""
                        +"${snap.title} - ${ldt}$scopeLabel"
                    }
                }
                
                ReactHTML.option {
                    value = "custom"
                    +"Custom time..."
                }
            }
        }
        
        // Manual time input - show always if no snapshots, or when "custom" is selected
        if (!hasSnapshots || selectedOption == "custom") {
            DateTimeInput {
                value = props.value?.toLocalDateTime(props.timezone)
                defaultDate = props.defaultDate
                onChange = { newVal, err ->
                    props.onChange(newVal?.toInstant(props.timezone))
                }
                placeholder = props.placeholder
            }
        }
    }
}

external interface UpdatePlotConfigDialogProps : BaseDialogProps {
    var question: Question
}

val UpdatePlotConfigDialog = dialogStateWrapper(FC<UpdatePlotConfigDialogProps> { props ->
    var time1 by useState<Instant?>(null)
    var time2 by useState<Instant?>(null)
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
                SnapshotOrTimeSelector {
                    title = "Before time (x axis)"
                    value = time1
                    onChange = { time1 = it }
                    snapshots = allSnapshots
                    showNowOption = false
                    timezone = tz
                    defaultDate = today
                }
                
                SnapshotOrTimeSelector {
                    title = "After time (y axis)"
                    value = time2
                    onChange = { time2 = it }
                    snapshots = allSnapshots
                    showNowOption = true
                    timezone = tz
                    defaultDate = today
                    placeholder = "(now)"
                }
            }
        }

        val data = useSuspendResult(props.open, time1, time2, q.id) {
            if (!props.open) return@useSuspendResult null
            val url = "/api${questionUrl(q.id)}/update_plot?t1=${time1?.epochSeconds}" +
                if (time2 != null) "&t2=${time2!!.epochSeconds}" else ""
            Client.httpClient.get(url).body<List<List2<Prediction?>>>()
        }?.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>?>()

        UpdateScatterPlot {
            this.preds = data ?: emptyList()
            this.mode = UpdateScatterMode.DIRECTIONAL
            this.referenceForecast = q.extensionData?.get(ReferenceForcastKey)
        }
        div {
            IconButton {
                PresenterIcon {}
                onClick = {
                    presenterCtl.offer(
                        UpdateScatterPlotPV(
                            q.ref,
                            time1,
                            time2
                        )
                    )
                }
            }
        }
    }
})

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
                                if (multiScope) td { +scope.name.lowercase().capFirst() } // XXX use a chip or something
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
                // only moderators can create public snapshots
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

enum class UpdateScatterMode {
    DIRECTIONAL,
    EXTREMES,
}

external interface UpdateScatterPlotProps : Props {
    var preds: List<List2<Prediction?>>
    var mode: UpdateScatterMode
    var diffCoords: Boolean?
    var referenceForecast: Double?
}

data class UpdateSize(
    val probRange: kotlin.ranges.ClosedFloatingPointRange<Double>,
    val name: String,
    val colors: List2<String>,
) {
    operator fun contains(diff: Double):Boolean {
        return abs(diff) in probRange && abs(diff) != probRange.endInclusive && diff != 0.0
    }
}

fun hues(hUp: Int, hDown: Int) = List2("hsl($hUp,35,80)", "hsl($hDown,35,80)")

val updateSizes = listOf(
    UpdateSize(0.0..0.05, "Small (0-5%)", List2("#aed581", "#ff8a65")),
    UpdateSize(0.05..0.15, "Medium (5-15%)", List2("#9ccc65", "#ff7043")),
    UpdateSize(0.15..0.3, "Large (15-30%)", List2("#8bc34a", "#ff5722")),
    UpdateSize(0.3..1.0, "XL (>30%)", List2("#7cb342", "#f4511e")),
)

val UpdateScatterPlot = FC<UpdateScatterPlotProps> { props->
    val preds = props.preds.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>>()
    val before = preds.map { (it[0].dist as BinaryDistribution).yesProb }
    val after = preds.map { (it[1].dist as BinaryDistribution).yesProb }
    val diffCoords = props.diffCoords?: false
    fun transform(xy: List2<Double>) = if (diffCoords) List2(xy.e1, xy.e2-xy.e1 ) else xy
    ReactPlotly {
        config = jso { responsive = true }
        traces = listOf(Scatter {
            console.log(ensureTwo(before).toTypedArray())
            console.log(ensureTwo(before.zip(after) { b,a -> transform(List2(b,a)).e2 }).toTypedArray())
            x.set(ensureTwo(before).toTypedArray())
            y.set(ensureTwo(before.zip(after) { b,a -> transform(List2(b,a)).e2 }).toTypedArray())
            this.mode = ScatterMode.markers
            marker {
                size = 10
                color("blue")
            }
        })
        plotlyInit = {plot->
            plot.layout {
                width = 400
                height = 400
                listOf(xaxis, yaxis).forEach { ax->
                    ax.apply {
                        range(plotlyVal(0), plotlyVal(1))
                        tickmode = TickMode.array
                        tickvals = listOf(0, 0.25, 0.5, 0.75, 1).map(::plotlyVal).toList()
                    }
                    if (diffCoords) {
                        yaxis {
                            range(plotlyVal(-0.3), plotlyVal(0.3))
                            tickvals = (listOf(0.0) + (updateSizes.map{it.probRange.endInclusive} cross listOf(-1.0,1.0)).map{ (x,y)->x*y }.toList()).map(::plotlyVal)
                        }
                    }
                }
                xaxis {
                    title = "Credence before"
                }
                yaxis {
                    title = if (diffCoords) "Credence change" else "Credence after"
                }
                buildShapes {
                    addShape { // frame
                        x0 = plotlyVal(0)
                        x1 = plotlyVal(1)
                        if (diffCoords) {
                            y0 = plotlyVal(-1)
                            y1 = plotlyVal(1)
                        } else {
                            y0 = plotlyVal(0)
                            y1 = plotlyVal(1)
                        }
                    }
                    if (!diffCoords) {
                        addShape { // main diagonal
                            type = ShapeType.line
                            x0 = plotlyVal(0)
                            y0 = plotlyVal(0)
                            x1 = plotlyVal(1)
                            y1 = plotlyVal(1)
                        }
                        props.referenceForecast?.let { ref ->
                            // Vertical reference line
                            addShape {
                                type = ShapeType.line
                                x0 = plotlyVal(ref)
                                y0 = plotlyVal(0)
                                x1 = plotlyVal(ref)
                                y1 = plotlyVal(1)
                                fillcolor("rgba(0, 0, 255, 0.5)")
                                line {
                                    width = 2
                                    dash = Dash.dash
                                }
                            }
                            // Horizontal reference line
                            addShape {
                                type = ShapeType.line
                                x0 = plotlyVal(0)
                                y0 = plotlyVal(ref)
                                x1 = plotlyVal(1)
                                y1 = plotlyVal(ref)
                                fillcolor("rgba(0, 0, 255, 0.5)")
                                line {
                                    width = 2
                                    dash = Dash.dash
                                }
                            }
                        }
                    } else {
                        props.referenceForecast?.let { ref ->
                            // Vertical reference line
                            addShape {
                                type = ShapeType.line
                                x0 = plotlyVal(ref)
                                y0 = plotlyVal(-0.3)
                                x1 = plotlyVal(ref)
                                y1 = plotlyVal(0.3)
                                fillcolor("rgba(0, 0, 255, 0.5)")


                                line {
                                    width = 2
                                    dash = Dash.dash
                                }
                            }
                        }

                    }
                    if (props.mode == UpdateScatterMode.DIRECTIONAL) {
                        var last = 0.0
                        updateSizes.forEach {us->
                            listOf(0,1).forEach { direction->
                                val coords = if (diffCoords)
                                    listOf(
                                        List2(0.0, us.probRange.start),
                                        List2(0.0, us.probRange.endInclusive),
                                        List2(1.0, us.probRange.endInclusive),
                                        List2(1.0, us.probRange.start),
                                    )
                                else
                                    listOf(
                                        List2(0.0, us.probRange.start),
                                        List2(0.0, us.probRange.endInclusive),
                                        List2(1.0 - us.probRange.endInclusive, 1.0),
                                        List2(1.0 - us.probRange.start, 1.0 ),
                                    )
                                val flippedCoords = coords.map{
                                    if (diffCoords) List2(it.e1, -it.e2) else it.reversed() }
                                addShape {
                                    type = ShapeType.path
                                    path = pathToSvg(if (direction == 1) flippedCoords else coords, close=true)
                                    layer = ShapeLayer.below
                                    fillcolor(us.colors[direction] + "cc")
                                    line {
                                        width=0
                                    }
                                }
                            }
                        }
                    }
                }
                margin {
                    this.t = 20
                }
            }
        }
        fixupLayout = {layout ->
            layout.xaxis.mirror = "ticks"
            layout.yaxis.mirror = true
            layout.xaxis.tickformat = ",.0%"
            layout.yaxis.tickformat = ",.0%"
            layout.font = jso{ size = 20 }
        }
    }
}

val UpdateScatterTable = FC<UpdateScatterPlotProps> { props->
    val preds = props.preds.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>>()
    val dists = preds.map { it.map { it.dist as? BinaryDistribution } }.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<BinaryDistribution>>>()
    val probs = dists.map { it.map { it.yesProb } }
    val diffs = probs.map { it[1] - it[0] }
    ReactHTML.table {
        css {
            fontSize = 22.px
            "td, th" {
                border = Border(1.px, LineStyle.solid, Color("#666"))
                padding = 4.px
            }
            "tbody td" {
                textAlign = TextAlign.right
            }
            "tbody th" {
                textAlign = TextAlign.left
            }
            borderCollapse = BorderCollapse.collapse
        }
        ReactHTML.thead {
            ReactHTML.tr {
                ReactHTML.th {
                    rowSpan = 2
                    +"Update size"
                }
                ReactHTML.th {
                    colSpan = 3
                    +"Update direction"
                }
            }
            ReactHTML.tr {
                ReactHTML.th { +"Up" }
                ReactHTML.th { +"Down" }
                ReactHTML.th { +"Total" }
            }
        }
        ReactHTML.tbody {
            ReactHTML.tr {
                ReactHTML.th { +"No change" }
                ReactHTML.td {}
                ReactHTML.td {}
                ReactHTML.td { +diffs.filter{ it == 0.0 }.size.toString() }
            }
            updateSizes.forEach { sz ->
                ReactHTML.tr {
                    ReactHTML.th { +sz.name }
                    val sizeGroup = diffs.filter{ it in sz }
                    val upGroup = sizeGroup.filter { it > 0.0 }
                    val downGroup = sizeGroup.filter { it < 0.0 }
                    ReactHTML.td {
                        css {
                            backgroundColor = Color(sz.colors[0])
                        }
                        +upGroup.size.toString()
                    }
                    ReactHTML.td {
                        css {
                            backgroundColor = Color(sz.colors[1])
                        }
                        +downGroup.size.toString()
                    }
                    ReactHTML.td { +sizeGroup.size.toString() }
                }
            }
            ReactHTML.tr {
                ReactHTML.th { +"Total" }
                val upGroup = diffs.filter { it > 0.0 }
                val downGroup = diffs.filter { it < 0.0 }
                ReactHTML.td { +upGroup.size.toString() }
                ReactHTML.td { +downGroup.size.toString() }
                ReactHTML.td { +diffs.size.toString() }
            }
        }
    }
}

val UpdateScatterPlotPP = FC<PresenterPageProps<UpdateScatterPlotPV>> { props ->
    val view = props.view
    val q = view.question.deref()
    val referenceForecast = q?.extensionData?.get(ReferenceForcastKey)
    val rawData = useSuspendResult(view.time1, view.time2, view.question.id) {
        val url = "/api${questionUrl(view.question.id)}/update_plot?x" +
                (if (view.time1 != null) "&t1=${view.time1!!.epochSeconds}" else "") +
                if (view.time2 != null) "&t2=${view.time2!!.epochSeconds}" else ""
        Client.httpClient.get(url).body<List<List2<Prediction?>>>()
    }
    val data = rawData?.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>?>()

    Stack {
        css {
            alignItems = AlignItems.center
            maxWidth = 100.vw
            width = 100.vw
            maxHeight = 100.vh
            height = 100.vh
        }
        q?.name?.let { h1 { +it } }
        Stack {
            direction = FlexDirection.row
            UpdateScatterPlot {
                this.preds = data ?: emptyList()
                this.mode = UpdateScatterMode.DIRECTIONAL
                this.referenceForecast = referenceForecast
            }
            UpdateScatterPlot {
                this.preds = data ?: emptyList()
                this.mode = UpdateScatterMode.DIRECTIONAL
                this.diffCoords = true
                this.referenceForecast = referenceForecast
            }
            UpdateScatterTable {
                this.preds = data ?: emptyList()
            }
        }
        if (rawData != null)
        Stack {
            direction = FlexDirection.row
            css {
                flexGrow = number(0.0)
                fontSize = 200.pct
                justifyContent = JustifyContent.spaceEvenly
                marginTop = 20.px
                alignSelf = AlignSelf.stretch
            }
            listOf(0,1).map { which->
                val gp = rawData.mapNotNull { (it[which]?.dist as? BinaryDistribution)?.yesProb }.average()
                if (gp.isNaN()) return@map
                div {
                    val adverb = listOf("before", "after")[which]
                    +"${(q?.groupTerminology ?: GroupTerminology.GROUP).term.capFirst()}"
                    +" ${(q?.predictionTerminology ?: PredictionTerminology.PREDICTION).term}"
                    +" $adverb: ${formatPercent(gp)}"
                }
            }
        }
    }
}

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
                var time1  by useContextState<Instant?>(ExtensionContextPlace.QUESTION_PAGE, "time1", default = null)
                var time2  by useContextState<Instant?>(ExtensionContextPlace.QUESTION_PAGE, "time2", default = null)
                val presenterCtl = use(PresenterContext)!!
                val tz = TimeZone.currentSystemDefault()
                val today = Clock.System.now().toLocalDateTime(tz).date
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
        mapOf(presenterPageMap(UpdateScatterPlotPP))
}
