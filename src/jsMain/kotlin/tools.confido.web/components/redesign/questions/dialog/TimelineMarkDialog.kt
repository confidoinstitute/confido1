package tools.confido.web.components.redesign.questions.dialog

import emotion.react.*
import kotlinx.datetime.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.api.Api
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.rooms.Room
import tools.confido.rooms.TimelineMark
import tools.confido.utils.unixNow
import tools.confido.web.Client
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.forms.*
import tools.confido.web.utils.runCoroutine
import web.cssom.*

external interface TimelineMarkDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
    var room: Room
    var question: Question
}

private fun markDate(ts: Int): LocalDate =
    Instant.fromEpochSeconds(ts.toLong()).toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * Moderator dialog for managing timeline markers: lists the question's and the
 * room's markers with delete buttons, and adds new ones (day precision; stored
 * as noon UTC of the picked date).
 */
val TimelineMarkDialog = FC<TimelineMarkDialogProps> { props ->
    var name by useState("")
    var date by useState<LocalDate?>(null)
    var roomWide by useState(false)

    fun submit() {
        val d = date ?: return
        val markName = name.trim().ifEmpty { return }
        val mark = TimelineMark(id = "", ts = d.atTime(12, 0).toInstant(TimeZone.UTC).epochSeconds.toInt(), name = markName)
        runCoroutine {
            if (roomWide) Client.call(Api.roomTimelineMarkAdd(props.room.ref), mark)
            else Client.call(Api.questionTimelineMarkAdd(props.question.ref), mark)
            name = ""
            date = null
        }
    }

    Dialog {
        +props
        title = "Timeline markers"
        Stack {
            css {
                padding = 15.px
                gap = 10.px
                fontFamily = sansSerif
                fontSize = 14.px
                borderTop = Border(1.px, LineStyle.solid, Color("#CCCCCC"))
            }

            val rows = props.question.timelineMarks.map { it to false } +
                    props.room.timelineMarks.map { it to true }
            if (rows.isEmpty()) {
                div {
                    css { color = Color("#999999") }
                    +"No timeline markers yet. Markers label moments like reviews or announcements on every prediction timeline."
                }
            }
            rows.sortedBy { it.first.ts }.forEach { (mark, isRoomWide) ->
                Stack {
                    direction = FlexDirection.row
                    css { gap = 8.px; alignItems = AlignItems.center }
                    span {
                        css { color = Color("#999999"); fontSize = 12.px; whiteSpace = WhiteSpace.nowrap }
                        +markDate(mark.ts).toString()
                    }
                    span { css { flexGrow = number(1.0) }; +mark.name }
                    if (isRoomWide) {
                        span {
                            css { fontSize = 11.px; color = Color("#888888"); whiteSpace = WhiteSpace.nowrap }
                            +"whole room"
                        }
                    }
                    ButtonUnstyled {
                        css { color = Color("#999999"); fontSize = 14.px; padding = 2.px }
                        +"✕"
                        onClick = {
                            runCoroutine {
                                if (isRoomWide) Client.call(Api.roomTimelineMarkDelete(props.room.ref, mark.id))
                                else Client.call(Api.questionTimelineMarkDelete(props.question.ref, mark.id))
                            }
                        }
                    }
                }
            }

            Stack {
                direction = FlexDirection.row
                css { gap = 8.px; alignItems = AlignItems.center; flexWrap = FlexWrap.wrap }
                TextInput {
                    placeholder = "Marker name"
                    value = name
                    onChange = { ev -> name = ev.target.value }
                }
                DateInput {
                    value = date
                    max = markDate(unixNow())
                    onChange = { newVal, err -> date = if (err == null) newVal else null }
                }
                Button {
                    css { margin = 0.px; padding = Padding(7.px, 12.px); fontSize = 13.px }
                    disabled = name.isBlank() || date == null
                    onClick = { submit() }
                    +"Add marker"
                }
            }
            Stack {
                direction = FlexDirection.row
                css { gap = 8.px; alignItems = AlignItems.center }
                Checkbox {
                    sz = 20.0
                    checked = roomWide
                    onChange = { ev -> roomWide = ev.target.checked }
                }
                span {
                    css { fontSize = 13.px; color = Color("#666666"); cursor = Cursor.pointer }
                    onClick = { roomWide = !roomWide }
                    +"Whole room (shown on all questions' timelines)"
                }
            }
        }
    }
}
