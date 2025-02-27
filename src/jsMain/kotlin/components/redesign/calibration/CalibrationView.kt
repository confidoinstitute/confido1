package components.redesign.calibration

import Client
import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.presenter.PresenterContext
import components.redesign.questions.predictions.yesGreen
import components.showError
import csstype.*
import dom.html.HTMLTableCellElement
import emotion.react.css
import extensions.UpdateScatterPlotPV
import hooks.combineRefs
import hooks.useCoroutineLock
import hooks.useSuspendResult
import icons.ProjectorScreenOutlineIcon
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.datetime.Instant
import kotlinx.js.tupleOf
import payloads.requests.CalibrationRequest
import payloads.requests.CalibrationWho
import payloads.requests.Everyone
import payloads.requests.Myself
import payloads.responses.CalibrationQuestion
import react.*
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.TdHTMLAttributes
import rooms.Room
import rooms.RoomPermission
import tools.confido.calibration.CalibrationBin
import tools.confido.calibration.CalibrationVector
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.state.CalibrationReqPV
import tools.confido.utils.capFirst
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed
import users.UserType
import utils.except
import web.url.URLSearchParams
import kotlin.math.abs

fun params2request(params: URLSearchParams): CalibrationRequest {
    val rooms = params.getAll("room").map{ Ref<Room>(it) }.toSet().ifEmpty { null }
    val questions = params.getAll("q").map{ Ref<Question>(it) }.toSet().ifEmpty { null }
    val who = when (params.get("who")) {
        "myself" -> Myself
        "group" -> Everyone
        else -> Myself
    }
    val fromTime = params.get("from")?.let { try { Instant.parse(it) } catch (e: Exception) { null } }
    val toTime = params.get("to")?.let { try { Instant.parse(it) } catch (e: Exception) { null } }
    //val toTime = params.get("to")?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
    return CalibrationRequest(rooms=rooms, questions=questions, who=who, fromTime=fromTime, toTime=toTime)
}

fun request2params(req: CalibrationRequest): URLSearchParams {
    val lst = buildList {
        add("who" to when (req.who) {
            Myself -> "myself"
            Everyone -> "group"
        })
        req.rooms?.forEach { add("room" to it.id) }
        req.questions?.forEach { add("q" to it.id) }
        req.fromTime?.let { add("from" to it.toString()) }
        req.toTime?.let { add("to" to it.toString()) }
    }
    return URLSearchParams(lst.map { (a,b) -> tupleOf(a,b) }.toTypedArray())
}

external interface CalibrationTableProps: Props  {
    var data: CalibrationVector
    var who: CalibrationWho?
    var highlightBin: CalibrationBin?
    var onDetail: ((CalibrationBin)->Unit)?
    var onBinHover: ((CalibrationBin?)->Unit)?
    var onHelp: ((CalibrationHelpSection)->Unit)?
}

val CalibrationWho.adjective get() = when (this) { Myself -> "your"; Everyone -> "group"; else -> null }
fun CalibrationWho?.withAdjective(noun: String) = this?.adjective?.let { "$it $noun" } ?: noun



val CalibrationTable = FC<CalibrationTableProps> { props->
    fun fmtp(p: Double) = (100*p).toFixed(1).trimEnd('0').trimEnd('.')+"%"
    val calib = props.data
    val entries = calib.entries.filter {it.value.total != 0}.sortedBy { it.key }
    if (entries.size > 0)
    table {
        css(baseTableCSS) {
        }
        thead {
            tr {
                th {
                    +props.who.withAdjective("confidence").capFirst()
                    InlineHelpButton {
                        onClick = { props.onHelp?.invoke(CalibrationHelpSection.CONFIDENCE) }
                    }
                }
                th {}
                th {
                    +props.who.withAdjective("accuracy").capFirst()
                    InlineHelpButton {
                        onClick = { props.onHelp?.invoke(CalibrationHelpSection.ACCURACY) }
                    }
                }
                th { +"Questions" }
                th {
                    +"Result"
                    InlineHelpButton {
                        onClick = { props.onHelp?.invoke(CalibrationHelpSection.RESULTS) }
                    }
                }
            }
        }
        tbody {
            entries.forEach { (bin,entry)->
                val calibrationOff = entry.successRate!! - bin.mid
                val band = calibrationBands.first { calibrationOff in it.range }

                val btd = FC<TdHTMLAttributes<HTMLTableCellElement>> { btdProps->
                    td {
                        +btdProps.except("children")
                        if (props.onDetail != null) {
                            ButtonUnstyled {
                                css { width = 100.pct; cursor = Cursor.pointer }
                                onClick = {
                                    props.onDetail?.invoke(bin)
                                }
                                ariaLabel = "View calibration details for the ${bin.formattedRange} confidence bracket"
                                +btdProps.children
                            }
                        } else {
                            +btdProps.children
                        }
                    }
                }
                tr {
                    css {
                        "td" {
                            backgroundColor = if (bin == props.highlightBin) Color("#eee") else NamedColor.white
                        }
                    }
                    onMouseEnter = { props.onBinHover?.invoke(bin) }
                    onMouseLeave = { props.onBinHover?.invoke(null) }
                    btd {
                        +"${fmtp(bin.range.start)} - ${formatPercent(bin.range.endInclusive)}"
                    }
                    btd {
                        css {
                            "&&" {
                                backgroundColor = Color(band.color)
                            }
                        }
                        +band.sign
                    }
                    btd {
                        entry.successRate?.let{ +fmtp(it) }
                    }
                    btd {
                        span {
                            if (props.onDetail != null)
                                css { +linkCSS }
                            +"${entry.total}"
                            //+" ${entry.total} "
                        }
                    }
                    btd {
                        css {
                            "&&" {
                                backgroundColor = Color(band.color)
                                fontWeight = integer(600)
                            }
                        }
                        if (0.0 in band.range) { // well-calibrated band
                            Stack {
                                direction = FlexDirection.row
                                css {
                                    justifyContent = JustifyContent.center
                                }
                                div {
                                    +band.name
                                }
                                div {
                                    css {
                                        width = 0.px
                                        flexBasis = 0.px
                                        flexGrow = number(0.0)
                                        position = Position.relative
                                    }
                                    div {
                                        css {
                                            color = yesGreen
                                            fontSize = 150.pct
                                            lineHeight = number(1.0)
                                            position = Position.absolute
                                            left = 0.px
                                            top = (-10).pct
                                        }
                                        +"✓"
                                    }
                                }
                            }
                        } else {
                            +band.name
                        }
                    }
                }
            }
        }
    }
}

external interface CalibrationViewBaseProps: PropsWithClassName {
    var graphHeight: Double?
    var graphGrid: Boolean?
    var graphAreaLabels: Boolean?
    var showGraph: Boolean?
    var showTable: Boolean?
    var graphContentOnly: Boolean?
    var externalHelpOpen: MutableRefObject<(CalibrationHelpSection)->Unit>?
    var onHelpChange: ((Boolean)->Unit)?
}
external interface CalibrationViewProps : CalibrationViewBaseProps {
    var data : CalibrationVector?
    var who: CalibrationWho?
    var onDetail: ((CalibrationBin)->Unit)?
}
val CalibrationView = FC<CalibrationViewProps> { props->
    val calib = props.data
    var hoveredBin by useState<CalibrationBin>()
    var helpSectionOpen by useState<CalibrationHelpSection>()
    CalibrationHelpDialog {
        open = helpSectionOpen != null
        initialSection = helpSectionOpen
        onClose = { helpSectionOpen = null }
    }
    useEffect(helpSectionOpen != null) {
        props.onHelpChange?.invoke(helpSectionOpen != null)
    }
    useEffect {
        props.externalHelpOpen?.current = { helpSectionOpen = it }
        cleanup { props.externalHelpOpen?.current = null }
    }
    // Format with one decimal points because midpoints of 50-55 and 95-100 bins are
    // decimal numbers that seem weird when rounded
    if (calib != null) {
        Stack {
            css(ClassName("calibration-view"), override = props) {
                gap = 15.px
                alignItems = AlignItems.stretch
            }
            if (props.showGraph ?: true)
            div {
                css {
                    backgroundColor = NamedColor.white
                }
                (if (props.graphContentOnly ?: false) CalibrationGraphContent else CalibrationGraph) {
                    this.calib = calib
                    this.height = props.graphHeight?.px
                    this.grid = props.graphGrid
                    this.who = props.who
                    this.areaLabels = props.graphAreaLabels
                    this.highlightBin = hoveredBin
                    this.onBinHover = { hoveredBin = it }
                    this.onHelp = { helpSectionOpen = it }
                }
            }
            if (props.showTable ?: true)
            CalibrationTable {
                this.data = calib
                this.who = props.who
                this.onDetail = props.onDetail
                this.highlightBin = hoveredBin
                this.onBinHover = { hoveredBin = it }
                this.onHelp = { helpSectionOpen = it }
            }
        }
    }
}

external interface CalibrationReqViewProps : CalibrationViewBaseProps {
    var req: CalibrationRequest
    var underTabs: ReactNode?
}

val CalibrationReqView = FC<CalibrationReqViewProps> { props->
    val data = useSuspendResult(props.req.identify()) {
        val resp = Client.sendDataRequest("/calibration", props.req)
        if (resp.status.isSuccess()) resp.body<CalibrationVector>()
        else null
    }
    val req = props.req
    val entries = data?.entries?.filter { it.value.total > 0 } ?: emptyList()
    val (appState,stale) = useContext(AppStateContext)
    val detailFetch = useCoroutineLock()
    var detailBin by useState<CalibrationBin>()
    val calibrationHelpOpen = useRef<(CalibrationHelpSection)->Unit>()
    var detail by useState<List<CalibrationQuestion>>()
    var helpOpen by useState(false)
    val presenterCtl = useContext(PresenterContext )
    val layoutMode = useContext(LayoutModeContext)
    val canDetail = (
            entries.size > 0 &&
                (req.who == Myself || (
                    req.rooms != null
                    && req.rooms.all { it.deref()?.hasPermission(appState.session.user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)?:false }
                )
            )
        )
    if (canDetail && detail != null) {
        CalibrationDetailDialog {
            open = detailBin != null && !helpOpen
            this.data = detail!!
            bin = detailBin
            onClose = { detailBin = null }
            onHelp = {
                calibrationHelpOpen.current?.invoke(it)
            }
        }
    }
    div {
        css {
            position = Position.relative
        }
        CalibrationView {
            this.data = data
            if (canDetail) {
                this.onDetail = { bin ->
                    if (detail == null) {
                        detailFetch {
                            Client.sendData("/calibration/detail", props.req, onError = { showError(it) }) {
                                detail = body<List<CalibrationQuestion>>()
                            }
                        }
                    }
                    detailBin = bin
                }
            }
            this.who = props.req.who
            +props.except("req", "externalHelpOpen")
            this.externalHelpOpen = combineRefs(calibrationHelpOpen, props.externalHelpOpen)
            onHelpChange = { helpOpen = it }
        }
        if (appState.session.user?.type != UserType.GUEST && layoutMode >= LayoutMode.TABLET && req.who != Myself)
        div {
            css {
                position = Position.absolute
                right = 0.px
                top = 0.px
            }
            IconButton {
                ProjectorScreenOutlineIcon{}
                onClick = {  presenterCtl.offer(CalibrationReqPV(req)) }
            }

        }
    }
}

val TabbedCalibrationReqView = FC<CalibrationReqViewProps> {props->
    var who by useState(props.req.who)
    val req = props.req.copy(who = who)
    MobileSidePad {
        OptionGroup<CalibrationWho>()() {
            variant = OptionGroupPageTabsVariant
            options = listOf(
                Myself to "Your calibration",
                Everyone to "Group calibration",
            )
            value = who
            onChange = { who = it }
        }
    }
    +props.underTabs
    CalibrationReqView {
        this.req = req
        +props.except("req")
    }
}