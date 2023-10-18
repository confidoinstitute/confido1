package components.redesign.calibration

import csstype.NamedColor
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
    fun fmtp(p: Double) = (100*p).toFixed(1).trimEnd('0').trimEnd('.')+"%"
    if (calib != null) {
        div {
            css {
                backgroundColor = NamedColor.white
            }
            CalibrationGraph {
                this.calib = calib
                this.height = props.graphHeight
            }
        }
        table {
            thead {
                tr {
                    th { +"Confidence range" }
                    th { +"Ideal accuracy" }
                    th { +"${when (props.who) { Myself -> "My"; Everyone -> "Group"; else -> "Actual" }} accuracy" }
                    th { +"Correct answers" }
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
                            +"${entry.counts[true]} / ${entry.total}"
                        }
                    }
                }
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
