package components.redesign.rooms.dialog

import browser.document
import components.AppStateContext
import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.forms.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.dialog.QuestionType
import components.rooms.RoomContext
import csstype.*
import dom.html.HTMLAnchorElement
import emotion.react.css
import io.ktor.http.*
import react.*
import react.dom.html.ButtonType
import react.dom.html.InputType
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import rooms.ExportHistory
import rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.NumericSpace
import tools.confido.utils.forEachDeref

external  interface  CsvExportDialogProps : Props {
    var open: Boolean
    var onClose: (()->Unit)?
    var question: Question?
}
val CsvExportDialog = FC<CsvExportDialogProps> {props->
    val (appState,stale) = useContext(AppStateContext)
    var exportWhat by useState("predictions")
    val room = useContext(RoomContext)
    var individual by useState(false)
    var exportHistory by useState(ExportHistory.LAST)
    var bucketsStr by useState("32")
    var buckets by useState(32)
    val selectedQuestions = props.question?.let { listOf(it.ref) } ?: room.questions
    val layoutMode = useContext(LayoutModeContext)

    val selectedNumeric = useMemo(selectedQuestions) {
        selectedQuestions.any { it.deref()?.answerSpace is NumericSpace }
    }

    val canScored = useMemo(selectedQuestions.joinToString(",") { it.id }) {
        selectedQuestions.all { it.deref()?.effectiveSchedule?.score != null }
    }

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
                                if (appState.hasPermission(room, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS))
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