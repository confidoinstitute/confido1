package components.questions

import Client
import components.AppStateContext
import components.DistributionSummary
import components.StatelessIconToggleButton
import csstype.WhiteSpace
import csstype.Width
import emotion.react.css
import icons.*
import mui.material.*
import mui.system.responsive
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionFlag
import payloads.requests.EditQuestionFieldType
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.abbr
import react.dom.html.ReactHTML.col
import react.dom.html.ReactHTML.colgroup
import react.dom.html.ReactHTML.span
import rooms.Room
import rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.state.havePermission
import tools.confido.utils.randomString
import utils.breakLines

external interface QuestionTableProps : Props {
    var room: Room
    var questions: List<Question>
    var allowEditingQuestions: Boolean
}

val QuestionTable = FC<QuestionTableProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    val room = props.room

    var editQuestion by useState<Question?>(null)
    var editQuestionKey by useState("")
    var editOpen by useState(false)
    useLayoutEffect(editOpen) {
        if (editOpen)
            editQuestionKey = randomString(20)
    }

    var expandedQuestion by useState<String?>(null)

    EditQuestionDialog {
        key = "##editDialog##$editQuestionKey"
        question = editQuestion
        open = editOpen
        onClose = { editOpen = false }
    }

    fun editQuestionOpen(it: Question) {
        editQuestion = it; editOpen = true
    }

    fun postEditQuestion(id: String, field: EditQuestionFieldType, value: Boolean) {
        val editQuestion: EditQuestion = EditQuestionFlag(field, value)
        Client.postData("/questions/$id/edit", editQuestion)
    }

    val showGroupPredCol = (
            props.questions.any{it.groupPred != null}
                    && (
                        room.havePermission(RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
                        || props.questions.any{it.groupPredVisible}
                        )
            )
    val showResolutionCol = (
            props.questions.any{it.resolution != null}
                    && (
                    room.havePermission(RoomPermission.VIEW_ALL_RESOLUTIONS)
                            || props.questions.any{it.resolutionVisible}
                    )
            )

    TableContainer {
        component = Paper
        Table {
            colgroup {
                col { css { width = "auto".asDynamic() as Width } }
                col {}
                repeat(3) {
                    col { css { width = "auto".asDynamic() as Width } }
                }
            }
            TableHead {
                TableRow {
                    TableCell {}
                    TableCell { +"Question" }
                    TableCell {
                        abbr {
                            title = "Number of people predicting, total number of prediction updates"
                            span {
                                css { whiteSpace = WhiteSpace.nowrap }
                                GroupsIcon { fontSize = SvgIconSize.inherit }
                                +" / "
                                TimelineIcon { fontSize = SvgIconSize.inherit }
                            }
                        }
                    }
                    if (showGroupPredCol) TableCell { +"Group pred." }
                    if (showResolutionCol) TableCell { +"Resolution" }
                }
            }
            TableBody {
                props.questions.map {question ->
                    TableRow {
                        TableCell {
                            Stack {
                                direction = responsive(StackDirection.row)
                                Tooltip {
                                    title = breakLines(
                                        if (question.visible) "Question is visible to forecasters.\nClick to hide."
                                        else "Question is hidden from forecasters.\nClick to show."
                                    )
                                    arrow = true
                                    span {
                                        StatelessIconToggleButton {
                                            on = question.visible
                                            onIcon = VisibilityIcon.create()
                                            offIcon = VisibilityOffOutlinedIcon.create()
                                            onChange = { postEditQuestion(question.id, EditQuestionFieldType.VISIBLE, it) }
                                        }
                                    }
                                }
                                Tooltip {
                                    title = breakLines(
                                        if (question.open) "Question is open for new predictions.\nClick to close."
                                        else "Question is closed (new predictions are not allowed).\nClick to open."
                                    )
                                    arrow = true
                                    span {
                                        StatelessIconToggleButton {
                                            on = question.open
                                            onIcon = LockOpenIcon.create()
                                            offIcon = LockIcon.create()
                                            onChange = { postEditQuestion(question.id, EditQuestionFieldType.OPEN, it) }
                                        }
                                    }
                                }
                                IconButton {
                                    EditIcon()
                                    onClick = {editQuestionOpen(question)}
                                }
                            }
                        }
                        TableCell { +question.name }
                        TableCell {
                            +"${question.numPredictors} "
                            +" / "
                            +"${question.numPredictions}"
                        }
                        if (showGroupPredCol)
                            TableCell {
                                StatelessIconToggleButton {
                                    on = question.groupPredVisible
                                    onIcon = VisibilityIcon.create()
                                    offIcon = VisibilityOffOutlinedIcon.create()
                                    onChange = { postEditQuestion(question.id, EditQuestionFieldType.GROUP_PRED_VISIBLE, it) }
                                }
                                question.groupPred?.dist?.let { groupDist->
                                    DistributionSummary {
                                        distribution = groupDist
                                        allowPlotDialog = true
                                    }
                                }
                            }
                        if (showResolutionCol)
                            TableCell {
                                question.resolution?.let {
                                    StatelessIconToggleButton {
                                        on = question.resolutionVisible
                                        onIcon = VisibilityIcon.create()
                                        offIcon = VisibilityOffOutlinedIcon.create()
                                        onChange = {
                                            postEditQuestion(
                                                question.id,
                                                EditQuestionFieldType.RESOLUTION_VISIBLE,
                                                it
                                            )
                                        }
                                    }
                                    +it.format()
                                }
                            }
                    }
                }
            }
        }
    }
}
