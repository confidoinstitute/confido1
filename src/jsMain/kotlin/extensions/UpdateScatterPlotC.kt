package extensions

import components.*
import components.AppStateContext
import components.presenter.PresenterButton
import components.presenter.PresenterPageProps
import components.presenter.PresenterPageType
import components.presenter.presenterPageMap
import components.redesign.basic.Dialog
import components.redesign.basic.DialogMenuItem
import components.redesign.basic.Stack
import components.redesign.forms.*
import components.redesign.presenter.PresenterContext
import components.redesign.questions.dialog.QuestionQuickSettingsDialogProps
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.rooms.RoomContext
import csstype.*
import dom.html.HTML.h1
import emotion.react.css
import extensions.UpdateScatterPlotCE.UpdatePlotState
import hooks.useSuspendResult
import icons.ProjectorScreenOutlineIcon
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.*
import kotlinx.js.jso
import react.*
import react.dom.html.AutoComplete
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.datalist
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.option
import react.router.useNavigate
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
import utils.questionUrl
import kotlin.math.abs
import kotlin.reflect.KClass

external interface QuestionSelectorProps : Props {
    var value: String?
    var onChange: ((Ref<Question>?) -> Unit)?
    var disabled: Boolean?
}

val QID_RE = Regex("\\[([a-zA-Z0-9_-]+)\\]\$")
val QuestionSelector = FC<QuestionSelectorProps>("QuestionSelector") { props ->
    val (appState, _) = useContext(AppStateContext)
    val datalistId = "question-list"
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
            type = react.dom.html.InputType.text
            placeholder = "Search for a question..."
            list = datalistId
            value = internalVal
            disabled = props.disabled == true
            autoComplete = AutoComplete.off
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

enum class UpdateScatterMode {
    DIRECTIONAL,
    EXTREMES,
}

external interface UpdateScatterPlotProps : Props {
    var preds: List<List2<Prediction?>>
    var mode: UpdateScatterMode
    var diffCoords: Boolean?
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
                    if (diffCoords) yaxis{
                        range(plotlyVal(-0.3), plotlyVal(0.3))
                        tickvals = (listOf(0.0) + (updateSizes.map{it.probRange.endInclusive} cross listOf(-1.0,1.0)).map{ (x,y)->x*y }.toList()).map(::plotlyVal)
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
                    if (!diffCoords)
                        addShape{ // main diagonal
                            type = ShapeType.line
                            x0 = plotlyVal(0)
                            y0 = plotlyVal(0)
                            x1 = plotlyVal(1)
                            y1 = plotlyVal(1)
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
    val view  = props.view
    val rawData = useSuspendResult(view.time1, view.time2, view.question.id) {
        val url = "/api${questionUrl(view.question.id)}/update_plot?x" +
                (if (view.time1 != null) "&t1=${view.time1!!.epochSeconds}" else "") +
                if (view.time2 != null) "&t2=${view.time2!!.epochSeconds}" else ""
        Client.httpClient.get(url).body<List<List2<Prediction?>>>()
    }
    val data = rawData?.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>?>()
    val q = view.question.deref()

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
            }
            UpdateScatterPlot {
                this.preds = data ?: emptyList()
                this.mode = UpdateScatterMode.DIRECTIONAL
                this.diffCoords = true
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
        val room = useContext(RoomContext)
        val (appState,_) = useContext(AppStateContext)
        if (appState.hasPermission(room, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS))
        cb.apply {
            DialogMenuItem {
                text = "Update plot"
                onClick = { updatePlotState = UpdatePlotState.CONFIG_OPEN; onClose() }
            }
        }
    }

    enum class UpdatePlotState {
        NONE, CONFIG_OPEN,
    }

    override fun questionPageExtra(q: Question, place: ClientExtension.QuestionPagePlace, cb: ChildrenBuilder) {
        when (place) {
            ClientExtension.QuestionPagePlace.QUESTION_PAGE_END -> {
                var state  by useContextState(ExtensionContextPlace.QUESTION_PAGE, "state", default = UpdatePlotState.NONE)
                var time1  by useContextState<Instant?>(ExtensionContextPlace.QUESTION_PAGE, "time1", default = null)
                var time2  by useContextState<Instant?>(ExtensionContextPlace.QUESTION_PAGE, "time2", default = null)
                val presenterCtl = useContext(PresenterContext)
                val tz = TimeZone.currentSystemDefault()
                val today = Clock.System.now().toLocalDateTime(tz).date
                val nav = useNavigate()
                val room = useContext(RoomContext)
                cb.apply {
                    Dialog {
                        open = (state == UpdatePlotState.CONFIG_OPEN)
                        onClose = { state = UpdatePlotState.NONE }
                        title = "Plot updates"
                        Form {
                            FormSection {
                                InputFormField<LocalDateTime, DateTimeInputProps>()() {
                                    title = "Before time (x axis)"
                                    inputComponent = DateTimeInput
                                    inputProps = jso {
                                        value = time1?.toLocalDateTime(tz)
                                        defaultDate = today
                                        onChange = { newVal, err ->
                                            time1 = newVal?.toInstant(tz)
                                        }
                                    }
                                }
                                InputFormField<LocalDateTime, DateTimeInputProps>()() {
                                    title = "After time (y axis)"
                                    inputComponent = DateTimeInput
                                    inputProps = jso {
                                        value = time2?.toLocalDateTime(tz)
                                        defaultDate = today
                                        onChange = { newVal, err ->
                                            time2 = newVal?.toInstant(tz)
                                        }
                                        placeholder = "(now)"
                                    }
                                }
                            }
                        }

                        val data = useSuspendResult(state, time1, time2, q.id) {
                            if (state != UpdatePlotState.CONFIG_OPEN) return@useSuspendResult null
                            val url = "/api${questionUrl(q.id)}/update_plot?t1=${time1?.epochSeconds}" +
                                if (time2 != null) "&t2=${time2!!.epochSeconds}" else ""
                            Client.httpClient.get(url).body<List<List2<Prediction?>>>()
                        }?.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>?>()

                        UpdateScatterPlot {
                            this.preds = data ?: emptyList()
                            this.mode = UpdateScatterMode.DIRECTIONAL
                        }
                        div {
                            IconButton {
                                ProjectorScreenOutlineIcon {}
                                disabled = time1 == null
                                onClick = {
                                    time1?.let { time1 ->
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
                    }
                }
            }
        }
    }

    override fun registerPresenterPages() =
        mapOf(presenterPageMap(UpdateScatterPlotPP))
}
