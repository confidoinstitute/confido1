package components.redesign.calibration

import components.redesign.basic.Stack
import components.redesign.forms.OptionGroup
import components.redesign.forms.OptionGroupPageTabsVariant
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import csstype.*
import emotion.react.css
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.datetime.Instant
import kotlinx.js.tupleOf
import payloads.requests.CalibrationRequest
import payloads.requests.CalibrationWho
import payloads.requests.Everyone
import payloads.requests.Myself
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import rooms.Room
import tools.confido.calibration.CalibrationVector
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.utils.capFirst
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed
import utils.except
import components.redesign.basic.css
import react.dom.html.ReactHTML.span
import web.url.URLSearchParams

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
}

val CalibrationWho.adjective get() = when (this) { Myself -> "your"; Everyone -> "group"; else -> null }
fun CalibrationWho?.withAdjective(noun: String) = this?.adjective?.let { "$it $noun" } ?: noun

val CalibrationTable = FC<CalibrationTableProps> { props->
    fun fmtp(p: Double) = (100*p).toFixed(1).trimEnd('0').trimEnd('.')+"%"
    val calib = props.data
    val entries = calib.entries.filter {it.value.total != 0}.sortedBy { it.key }
    val layoutMode = useContext(LayoutModeContext)
    if (entries.size > 0)
    table {
        css {
            borderCollapse = BorderCollapse.separate
            borderSpacing = 3.px
            "td, th" {
                textAlign = TextAlign.center
            }
            "thead th" {
                paddingLeft = 5.px // align with content
                fontWeight = integer(600)
                color = Color("#333")
                fontSize = 80.pct
            }
            "tbody td" {
                border = None.none
                backgroundColor = NamedColor.white
                //paddingLeft = 5.px
                //paddingRight = 5.px
                padding = 5.px
                verticalAlign = VerticalAlign.middle
            }
            if (layoutMode >= LayoutMode.TABLET) {
                "tbody tr:first-child td:first-child" {
                    borderTopLeftRadius = 10.px
                }
                "tbody tr:last-child td:first-child" {
                    borderBottomLeftRadius = 10.px
                }
                "tbody tr:first-child td:last-child" {
                    borderTopRightRadius = 10.px
                }
                "tbody tr:last-child td:last-child" {
                    borderBottomRightRadius = 10.px
                }
            }
            "tbody" {
                fontSize = 90.pct
            }
        }
        thead {
            tr {
                th { +props.who.withAdjective("confidence").capFirst() }
                th {}
                th { +props.who.withAdjective("accuracy").capFirst() }
                th { +"Questions" }
                th { +"Result" }
            }
        }
        tbody {
            entries.forEach { (bin,entry)->
                val calibrationOff = entry.successRate!! - bin.mid
                val band = calibrationBands.first { calibrationOff in it.range }
                tr {
                    td {
                        +"${fmtp(bin.range.start)} - ${formatPercent(bin.range.endInclusive)}"
                    }
                    td {
                        css {
                            "&&" {
                                backgroundColor = Color(band.color)
                            }
                        }
                        +band.sign
                    }
                    td {
                        entry.successRate?.let{ +fmtp(it) }
                    }
                    td {
                        +"${entry.total}"
                    }
                    td {
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
                                            color = Color("#00CC2E")
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
    var graphAreaLabels: Boolean?
    var showGraph: Boolean?
    var showTable: Boolean?
    var graphContentOnly: Boolean?
}
external interface CalibrationViewProps : CalibrationViewBaseProps {
    var data : CalibrationVector?
    var who: CalibrationWho?
}
val CalibrationView = FC<CalibrationViewProps> { props->
    val calib = props.data
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
                    this.height = props.graphHeight
                    this.who = props.who
                    this.areaLabels = props.graphAreaLabels
                }
            }
            if (props.showTable ?: true)
            CalibrationTable {
                this.data = calib
                this.who = props.who
            }
        }
    }
}

external interface CalibrationReqViewProps : CalibrationViewBaseProps {
    var req: CalibrationRequest
}

val CalibrationReqView = FC<CalibrationReqViewProps> { props->
    val data = useSuspendResult(props.req.identify()) {
        val resp = Client.sendDataRequest("/calibration", props.req)
        if (resp.status.isSuccess()) resp.body<CalibrationVector>()
        else null
    }
    CalibrationView {
        this.data = data
        this.who = props.req.who
        +props.except("req")
    }
}

val TabbedCalibrationReqView = FC<CalibrationReqViewProps> {props->
    var who by useState(props.req.who)
    val req = props.req.copy(who = who)
    val layoutMode = useContext(LayoutModeContext)
    OptionGroup<CalibrationWho>()() {
        variant = OptionGroupPageTabsVariant
        options = listOf(
            Myself to "Your calibration",
            Everyone to "Group calibration",
        )
        value = who
        onChange = { who = it }
        css {
            if (layoutMode == LayoutMode.PHONE) {
                paddingLeft = 15.px
                paddingRight = 15.px
            }
        }
    }
    CalibrationReqView {
        this.req = req
        +props.except("req")
    }
}