package components.redesign.questions.dialog

import components.redesign.CloseIcon
import components.redesign.basic.css
import components.redesign.basic.sansSerif
import components.redesign.forms.DateTimeInput
import components.redesign.forms.FormErrorCSS
import components.redesign.forms.FormField
import components.redesign.forms.IconButton
import csstype.FontStyle
import csstype.px
import kotlinx.datetime.*
import kotlinx.js.jso
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import tools.confido.question.QuestionSchedule
import tools.confido.question.QuestionState

external interface EditQuestionDialogScheduleProps: Props {
    var preset: QuestionPreset?
    var schedule: QuestionSchedule
    var onChange: ((QuestionSchedule, isError: Boolean)->Unit)?
    var showOpen: Boolean?
    var showScore: Boolean?
    var showClose: Boolean?
    var showResolve: Boolean?
    var openPlaceholder: String?
    var disabled: Boolean?
    var readOnly: Boolean?
}


val EditQuestionDialogSchedule = FC<EditQuestionDialogScheduleProps> { props ->
    val preset = props.preset ?: QuestionPreset.NONE
    val sched = props.schedule
    val disabled = props.disabled ?: false
    val readOnly = props.readOnly ?: false
    val baseId = useId()
    fun getError(sched: QuestionSchedule): String? {
        fun lt(a: Instant?, b: Instant?) = a != null && b != null && a < b
        return if (lt(sched.close, sched.open))
            "Closing time must be later than opening time"
        else if (lt(sched.resolve, sched.close))
            "Resolve time must be later than closing time"
        else if (lt(sched.score, sched.open))
            "Score time must be later than opening time"
        else
            null
    }
    fun ChildrenBuilder.schedItem(id: String, title: String, value: Instant?, placeholder: String?=null, transform: (Instant?)->QuestionSchedule) {
        val dateId = "$baseId.$id"
        val ldt = value?.toLocalDateTime(TimeZone.currentSystemDefault())
        tr {
            key = id
            td {
                label {
                    htmlFor = dateId
                    +title
                }
            }
            if (readOnly) {
                if (value == null)
                    td {
                        colSpan = 2
                        css { fontStyle = FontStyle.italic }
                        placeholder?.let{ +it }
                    }
                else {
                    td { + value.toJSDate().toLocaleDateString() }
                    td { + value.toJSDate().toLocaleTimeString() }
                }
            } else
                DateTimeInput {
                    this.value = ldt
                    this.placeholder = placeholder
                    this.defaultTime = LocalTime(0, 0)
                    //this.disabled = disabled
                    //this.readOnly = readOnly
                    dateProps = jso {
                        this.id = dateId
                    }
                    this.onChange = { newLDT, err ->
                        if (!disabled && !readOnly) {
                            val newInst = newLDT?.toInstant(TimeZone.currentSystemDefault())
                            val newSched = transform(newInst)
                            println("onchange $newSched")
                            props.onChange?.invoke(newSched, getError(newSched) != null)
                        }
                    }
                    this.wrap = { di, ti ->
                        Fragment.create {
                            td { +di }
                            td { +ti }
                        }
                    }
                }
            td {
                if (value != null) {
                    if (!disabled && !readOnly)
                    IconButton {
                        this.disabled = disabled
                        CloseIcon{}
                        onClick = {
                            if (!disabled && !readOnly) {
                                val newSched = transform(null)
                                props.onChange?.invoke(newSched, getError(newSched) != null)
                            }
                        }
                    }
                }
            }
        }
    }
    table {
        tbody {
            if (props.showOpen ?: true)
                schedItem("open", "Opens at", sched.open, placeholder=props.openPlaceholder ?: "manually") { sched.copy(open = it) }
            if (props.showScore ?: true)
                schedItem("score", "Scored at", sched.score, placeholder = "determined later") { sched.copy(score = it) }
            if (props.showClose ?: true)
                schedItem("close", "Closes at", sched.close, placeholder = "manually") { sched.copy(close = it) }
            if (props.showResolve ?: true)
                schedItem("resolve", "Resolves at", sched.resolve, placeholder = "manually") { sched.copy(resolve = it) }
        }
    }
    getError(sched)?.let {
        div {
            className = FormErrorCSS
            +it
        }
    }

}

