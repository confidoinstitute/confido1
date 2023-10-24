package components.redesign.calibration

import components.redesign.basic.Stack
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
import react.FC
import react.Props
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
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed
import utils.except
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

val CalibrationTable = FC<CalibrationTableProps> { props->
    fun fmtp(p: Double) = (100*p).toFixed(1).trimEnd('0').trimEnd('.')+"%"
    val calib = props.data
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
            "tbody" {
                fontSize = 90.pct
            }
        }
        val whos = when (props.who) { Myself -> "My"; Everyone -> "Group"; else -> "Actual" }
        thead {
            tr {
                th { +"$whos confidence" }
                th { +"Ideal accuracy" }
                th { +"$whos accuracy" }
                th { +"Correct answers" }
                th { +"Result" }
            }
        }
        tbody {
            calib.entries.filter {it.value.total != 0}.sortedBy { it.key }.forEach { (bin,entry)->
                tr {
                    td {
                        +"${fmtp(bin.range.start)} - ${formatPercent(bin.range.endInclusive)}"
                    }
                    td {
                        +fmtp(bin.mid)
                    }
                    td {
                        entry.successRate?.let{ +fmtp(it) }
                    }
                    td {
                        +"${entry.counts[true]} out of ${entry.total}"
                    }
                    td {
                        val calibrationOff = entry.successRate!! - bin.mid
                        val band = calibrationBands.first { calibrationOff in it.range }
                        css {
                            "&&" {
                                backgroundColor = Color(band.color)
                                fontWeight = integer(600)
                            }
                        }
                        +band.name
                    }
                }
            }
        }
    }
}

external interface CalibrationViewBaseProps: Props {
    var graphHeight: Double?
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
            css { gap = 15.px; paddingBottom = 30.px; }
            div {
                css {
                    backgroundColor = NamedColor.white
                }
                CalibrationGraph {
                    this.calib = calib
                    this.height = props.graphHeight
                }
            }
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
