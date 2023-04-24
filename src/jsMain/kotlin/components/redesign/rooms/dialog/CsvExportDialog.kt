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
import rooms.ExportHistory
import rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.spaces.NumericSpace
import tools.confido.utils.forEachDeref

external  interface  CsvExportDialogProps : Props {
    var open: Boolean
    var onClose: (()->Unit)?
}
val CsvExportDialog = FC<CsvExportDialogProps> {props->
    val (appState,stale) = useContext(AppStateContext)
    var exportWhat by useState("predictions")
    val room = useContext(RoomContext)
    var question by useState<Ref<Question>>()
    var individual by useState(false)
    var exportHistory by useState(ExportHistory.LAST)
    var bucketsStr by useState("32")
    var buckets by useState(32)
    val selectedQuestions = if (question == null) room.questions else listOf(question!!)
    val layoutMode = useContext(LayoutModeContext)

    val selectedNumeric = useMemo(selectedQuestions) {
        selectedQuestions.any { it.deref()?.answerSpace is NumericSpace }
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

        Form {
            onSubmit = { doExport() }
            FormSection {
                FormField {
                    title = "Question to export"
                    Select {
                        css {
                            width = important(100.pct)
                        }
                        value = question ?: ""
                        onChange = { event ->
                            question = event.target.value.ifEmpty { null }?.let { Ref(it) }
                        }
                        option {
                            +"(All questions)"
                            value = ""
                        }
                        room.questions.reversed().forEachDeref {
                            option {
                                +it.name
                                value = it.id
                            }
                        }
                    }
                }
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
                                "Answer range for each numeric question will be split into this many equal-sized buckets and a probability will be included for each bucket. In addition, a mean and standard deviation for the original distribution will be included."
                            //"Set range only if the answers out of it do not make sense (e.g. a negative duration of an event). In other cases, we recommend leaving them blank."
                            TextInput {
                                type = InputType.number
                                value = bucketsStr
                                onChange = { e -> bucketsStr = e.target.value }
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