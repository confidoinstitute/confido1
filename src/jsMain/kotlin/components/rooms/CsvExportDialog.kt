package components.rooms

import components.AppStateContext
import csstype.Flex
import csstype.Overflow
import csstype.px
import io.ktor.http.*
import mui.material.*
import mui.system.sx
import react.*
import react.dom.html.InputType
import react.dom.html.ReactHTML.em
import react.dom.onChange
import rooms.ExportHistory
import rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.refs.deref
import tools.confido.spaces.NumericSpace
import utils.*

val CsvExportDialog = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    val questions = useMemo(room.questions) {
        room.questions.mapNotNull { it.deref() }.sortedBy { it.name }
    }
    val questionCount = questions.size

    var open by useState(false)
    var selectedQuestions by useState<Set<Question>>(emptySet())

    val selectedNumeric = useMemo(selectedQuestions) {
        selectedQuestions.any { it.answerSpace is NumericSpace }
    }
    var buckets by useState(32)
    var bucketsText by useState(buckets.toString())

    var aggregate by useState(appState.hasPermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS))
    val canChangeAggregate = appState.hasAllPermissions(room, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
    var history by useState(ExportHistory.LAST)

    val params = parametersOf(
        "questions" to listOf(selectedQuestions.map { it.id }.joinToString(",")),
        "group" to listOf(aggregate.toString()),
        "history" to listOf(history.name),
        "buckets" to if (selectedNumeric) listOf(buckets.toString()) else emptyList(),
    )
    val href = "/export.csv?${params.formUrlEncode()}"

    Button {
        disabled = stale
        onClick = {open = true}
        +"Export to CSV"
    }

    Dialog {
        this.open = open
        this.fullWidth = true
        this.maxWidth = "sm"
        onClose = {_, _ -> open = false}
        DialogTitle {
            +"Export as CSV…"
        }
        DialogContent {
            sx {
                paddingBottom = themed(0)
                flex = Flex(themed(0), shrink=themed(0))
            }
            DialogContentText {
                +"Selected questions to export"
            }
        }
        List {
            sx {
                paddingTop = themed(0)
                overflowY = "auto".asDynamic()
                minHeight = 100.px
            }
            dense = true
            ListItemButton {
                ListItemIcon {
                    Checkbox {
                        edge = SwitchBaseEdge.start
                        size = Size.small
                        disableRipple = true
                        checked = selectedQuestions.size == questionCount
                        indeterminate = selectedQuestions.isNotEmpty() && selectedQuestions.size < questionCount
                    }
                }
                ListItemText {
                    primary = em.create { +"All..." }
                }
                onClick = {
                    selectedQuestions = if (selectedQuestions.size < questionCount)
                        questions.toSet()
                    else
                        emptySet()
                }
            }
            Divider {}
            questions.map {question ->
                ListItemButton {
                    key = question.id
                    ListItemIcon {
                        Checkbox {
                            size = Size.small
                            edge = SwitchBaseEdge.start
                            disableRipple = true
                            checked = question in selectedQuestions
                        }
                    }
                    ListItemText {
                        primary = ReactNode(question.name)
                        secondary = ReactNode(question.description)
                    }
                    onClick = {
                        selectedQuestions = selectedQuestions.xor(question)
                    }
                }
            }
        }
        DialogContent {
            sx {
                paddingTop = themed(0)
                overflowY = Overflow.visible
                flex = Flex(themed(0), shrink=themed(0))
            }
            if (canChangeAggregate)
            FormGroup {
                FormControl {
                    FormLabel {
                        +"Export predictions"
                    }
                    RadioGroup {
                        value = aggregate
                        row = true
                        onChange = { _, value ->
                            aggregate = when (value) {
                                "true" -> true
                                "false" -> false
                                else -> error("This cannot happen!")
                            }
                        }
                        FormControlLabel {
                            label = ReactNode("As group")
                            value = "true"
                            control = Radio.create {}
                        }
                        FormControlLabel {
                            label = ReactNode("Individual")
                            value = "false"
                            control = Radio.create {}
                        }
                    }
                }
            }
            FormGroup {
                FormControl {
                    FormLabel {
                        +"Prediction history"
                    }
                    RadioGroup {
                        value = history.name
                        row = true
                        onChange = { _, value ->
                            history = ExportHistory.valueOf(value)
                        }
                        FormControlLabel {
                            label = ReactNode("Last only")
                            value = ExportHistory.LAST.name
                            control = Radio.create {}
                        }
                        FormControlLabel {
                            label = ReactNode("Daily")
                            value = ExportHistory.DAILY.name
                            control = Radio.create {}
                        }
                        FormControlLabel {
                            label = ReactNode("Full")
                            value = ExportHistory.FULL.name
                            control = Radio.create {}
                        }
                    }
                }
            }
            if (selectedNumeric) {
                TextField {
                    margin = FormControlMargin.dense
                    type = InputType.number
                    value = bucketsText
                    fullWidth = true
                    this.inputProps = jsObject {
                        this.min = 0
                        this.step = 1
                    }.unsafeCast<InputBaseComponentProps>()
                    label = ReactNode("Bucket count")
                    helperText = ReactNode("Probability distribution will be split into this many equal-sized buckets.")
                    onChange = {
                        bucketsText = it.eventValue()
                        buckets = it.eventNumberValue().toInt()
                    }
                }
            }
        }

        DialogActions {
            Button {
                disabled = stale || selectedQuestions.isEmpty()
                this.onClick = {open = false}
                asDynamic().target = "_blank"
                this.href = href
                +"Export"
            }
        }
    }
}