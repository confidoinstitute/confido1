package components.redesign.questions.dialog

import components.redesign.CloseIcon
import components.redesign.basic.sansSerif
import components.redesign.forms.DateTimeInput
import components.redesign.forms.FormField
import components.redesign.forms.IconButton
import csstype.px
import kotlinx.datetime.*
import kotlinx.js.jso
import react.*
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
    var onChange: ((QuestionSchedule)->Unit)?
    var showOpen: Boolean?
    var showScore: Boolean?
    var showClose: Boolean?
    var showResolve: Boolean?
    var openPlaceholder: String?
}


val EditQuestionDialogSchedule = FC<EditQuestionDialogScheduleProps> { props ->
    val preset = props.preset ?: QuestionPreset.NONE
    val sched = props.schedule
    val baseId = useId()
    fun ChildrenBuilder.schedItem(id: String, title: String, value: Instant?, placeholder: String?=null, transform: (Instant?)->QuestionSchedule) {
        val dateId = "$baseId.$id"
        tr {
            td {
                label {
                    htmlFor = dateId
                    +title
                }
            }
            DateTimeInput {
                this.value = value?.toLocalDateTime(TimeZone.currentSystemDefault())
                this.placeholder = placeholder
                this.defaultTime = LocalTime(0, 0)
                dateProps = jso {
                    this.id = dateId
                }
                this.onChange = { newLDT ->
                    val newInst = newLDT?.toInstant(TimeZone.currentSystemDefault())
                    val newSched = transform(newInst)
                    println("onchange $newSched")
                    props.onChange?.invoke(newSched)
                }
                this.wrap = { di, ti -> Fragment.create {
                    td { +di }
                    td { +ti }
                } }
            }
            td {
                if (value != null) {
                    IconButton {
                        CloseIcon{}
                        onClick = {
                            val newSched = transform(null)
                            props.onChange?.invoke(newSched)
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

}

