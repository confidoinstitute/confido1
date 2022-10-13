package components

import Client
import mui.material.*
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionFlag
import payloads.requests.EditQuestionFieldType
import react.FC
import react.Props
import react.useContext
import tools.confido.question.Question

external interface EditQuestionProps : Props {
    var questions: List<Question>
    var allowEditingQuestions: Boolean
}

val EditQuestions = FC<EditQuestionProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    
    fun postEditQuestion(id: String, field: EditQuestionFieldType, value: Boolean) {
        val editQuestion: EditQuestion = EditQuestionFlag(field, value)
        Client.postData("/questions/$id/edit", editQuestion)
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
                    TableCell { +"Resolution" }
                }
            }
            TableBody {
                props.questions.map {question ->
                    TableRow {
                        TableCell { +question.name }
                        listOf(
                            question.visible to EditQuestionFieldType.VISIBLE,
                            question.open to EditQuestionFieldType.OPEN,
                            question.groupPredVisible to EditQuestionFieldType.GROUP_PRED_VISIBLE,
                        ).map {(current, field) ->
                            TableCell {
                                Checkbox {
                                    disabled = !props.allowEditingQuestions || stale
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
