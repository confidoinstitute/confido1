package components

import Client
import mui.material.*
import tools.confido.payloads.*
import react.FC
import react.Props
import react.useContext

val EditQuestions = FC<Props> {
    val appState = useContext(AppStateContext)
    
    fun postEditQuestion(id: String, field: EditQuestionFieldType, value: Boolean) {
        val editQuestion: EditQuestion = EditQuestionField(field, value)
        Client.postData("/edit_question/$id", editQuestion)
    }

    TableContainer {
        component = Paper
        Table {
            TableHead {
                TableRow {
                    TableCell { +"Question" }
                    TableCell { +"Visible" }
                    TableCell { +"Enabled" }
                    TableCell { +"Predictions visible" }
                    TableCell { +"Resolved" }
                    TableCell { +"Resolution" }
                }
            }
            TableBody {
                appState.questions.values.map {question ->
                    TableRow {
                        TableCell { +question.name }
                        listOf(
                            question.visible to EditQuestionFieldType.VISIBLE,
                            question.enabled to EditQuestionFieldType.ENABLED,
                            question.predictionsVisible to EditQuestionFieldType.PREDICTIONS_VISIBLE,
                            question.resolved to EditQuestionFieldType.RESOLVED,
                        ).map {(current, field) ->
                            TableCell {
                                Checkbox {
                                    disabled = !appState.isAdmin
                                    checked = current
                                    onChange = { _, checked -> postEditQuestion(question.id, field, checked) }
                                }
                            }
                        }
                        TableCell {
                            TextField {
                                size = Size.small
                                disabled = true
                            }
                        }
                    }
                }
            }
        }
    }
}