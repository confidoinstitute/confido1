package tools.confido.web.components.redesign.rooms.dialog

import web.dom.document
import tools.confido.web.components.usePermission
import tools.confido.web.components.redesign.basic.Dialog
import tools.confido.web.components.redesign.basic.Stack
import tools.confido.web.components.redesign.forms.*
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.components.redesign.questions.dialog.QuestionType
import tools.confido.web.components.redesign.rooms.RoomContext
import web.cssom.*
import web.html.HTMLAnchorElement
import emotion.react.css
import io.ktor.http.*
import react.*
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import tools.confido.rooms.ExportHistory
import tools.confido.rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.spaces.NumericSpace
import web.html.ButtonType
import web.html.InputType
import web.html.number
import web.html.submit
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import tools.confido.state.Topics

external  interface  CsvExportDialogProps : Props {
    var open: Boolean
    var onClose: (()->Unit)?
    var question: Question?
}
val CsvExportDialog = FC<CsvExportDialogProps> {props->
    var exportWhat by useState("predictions")
    val room = use(RoomContext)!!
    var individual by useState(false)
    var exportHistory by useState(ExportHistory.LAST)
    var bucketsStr by useState("32")
    var buckets by useState(32)
    val selectedQuestions = props.question?.let { listOf(it.ref) } ?: room.questions
    val roomQuestions = (useSubscription(Topics.roomQuestions(room.ref)) as? WSData)?.data ?: emptyMap()
    val layoutMode = use(LayoutModeContext)!!

    val selectedNumeric = useMemo(selectedQuestions, roomQuestions) {
        selectedQuestions.any { roomQuestions[it.id]?.answerSpace is NumericSpace }
    }

    val canScored = useMemo(selectedQuestions.joinToString(",") { it.id }, roomQuestions) {
        selectedQuestions.all { roomQuestions[it.id]?.effectiveScheduleIn(room)?.score != null }
    }

    // Sensitive questions have no individual export (server-enforced for everyone,
    // moderators included); a mixed selection exports the non-sensitive ones only.
    val canIndividual = usePermission(room, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS)
    val sensitiveCount = selectedQuestions.count { roomQuestions[it.id]?.sensitive == true }

    val params = parametersOf(
        "questions" to listOf(selectedQuestions.map { it.id }.joinToString(",")),
        "what" to listOf(exportWhat),
        "group" to if (exportWhat == "predictions") listOf((!individual).toString()) else emptyList(),
        "history" to if (exportWhat == "predictions") listOf(exportHistory.name) else emptyList(),
        "buckets" to if (exportWhat == "predictions" && selectedNumeric) listOf(buckets.toString()) else emptyList(),
    )
    val href = "/export.csv?${params.formUrlEncode()}"

    fun doExport() {
        console.log("DO EXPORT $href")
        val link = document.createElement("a").unsafeCast<HTMLAnchorElement>()
        link.href = href
        link.download = "export.csv"
        link.click()
    }
    Dialog {
        open = props.open
        onClose = props.onClose
        title = "Export to CSV"
        action = "Export"
        onAction = { doExport() }

        p {
            if (props.question == null) {
                +"Exporting all questions in room "
                i { +room.name }
            } else {
                +"Exporting question "
                i { +props.question!!.name }
            }
        }
        Form {
            onSubmit = { doExport() }
            FormSection {
                FormField {
                    title = "Export"
                    OptionGroup<String>()() {
                        options = listOf(
                            "predictions" to "Predictions",
                            "comments" to "Comments",
                        )
                        defaultValue = "predictions"
                        value = exportWhat
                        onChange = { type -> exportWhat = type }
                    }
                }
                if (exportWhat == "predictions") {
                    Stack {
                        if (layoutMode >= LayoutMode.TABLET) {
                            direction = FlexDirection.row
                            css {
                                justifyContent = JustifyContent.spaceBetween
                                flexWrap = FlexWrap.wrap
                                alignItems = AlignItems.flexStart
                            }
                        } else {
                            direction = FlexDirection.column
                        }
                        RadioGroup<Boolean>()() {
                            title = "Export predictions as..."
                            options = buildList {
                                add(false to "Group aggregate")
                                if (canIndividual && sensitiveCount < selectedQuestions.size)
                                    add(true to "Individual predictions")
                            }
                            value = individual
                            onChange = { value -> individual = value }
                        }
                        RadioGroup<ExportHistory>()() {
                            title = "Include prediction history"
                            options = buildList {
                                add(ExportHistory.LAST to "Most recent prediction only")
                                if (canScored)
                                    add(ExportHistory.LAST_SCORED to "Most recent scored prediction")
                                add(ExportHistory.DAILY to "Daily updates")
                                add(ExportHistory.FULL to "All updates")
                            }
                            value = exportHistory
                            onChange = { value -> exportHistory = value }
                        }
                    }
                    if (individual && sensitiveCount > 0) {
                        p {
                            +"Sensitive questions are excluded from individual exports."
                        }
                    }
                    if (selectedNumeric && !individual) {

                        FormField {
                            title = "Bucket count"
                            comment =
                                "Answer range${ if (selectedQuestions.size > 1) " for each numeric question" else ""} will be split into this many equal-sized buckets and a probability will be included for each bucket. In addition, a mean and standard deviation for the original distribution will be included."
                            //"Set range only if the answers out of it do not make sense (e.g. a negative duration of an event). In other cases, we recommend leaving them blank."
                            TextInput {
                                type = InputType.number
                                value = bucketsStr
                                onChange = { e -> bucketsStr = e.target.value; buckets = e.target.value.toIntOrNull() ?: 32 }
                            }
                        }
                    }
                }
            }
            Stack {
                Button {
                    type = ButtonType.submit
                    css {
                        margin = Margin(20.px, 20.px, 10.px)
                        display = Display.block
                        fontWeight = integer(500)
                    }
                    +"Export"
                }
            }
        }

    }
}