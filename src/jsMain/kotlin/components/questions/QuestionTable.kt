package components.questions

import Client
import components.AppStateContext
import components.DistributionSummary
import components.IconToggleButton
import csstype.*
import emotion.react.css
import hooks.useEditDialog
import icons.*
import mui.material.*
import mui.material.styles.TypographyVariant
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
import tools.confido.refs.HasId
import tools.confido.state.havePermission
import tools.confido.utils.randomString
import utils.*

external interface QuestionTableProps : Props {
    var room: Room
    var questions: List<Question>
    var allowEditingQuestions: Boolean
}


val QuestionTable = FC<QuestionTableProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    val room = props.room

    val editQuestionOpen = useEditDialog(EditQuestionDialog)

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

    Typography {
        this.variant = TypographyVariant.body1
        css { fontStyle = FontStyle.italic; fontSize = FontSize.smaller; paddingBottom = 5.px; }
        +"This table gives a quick and condensed moderator overview of the questions in the room."
        +"It also allows for basic question management such as showing/hiding questions."
        +"If you hover your mouse cursor over an icon or other similar element, an explanation of its function will be shown."
    }
    TableContainer {
        // apparently, this is the only way? (https://stackoverflow.com/questions/4757844/css-table-column-autowidth)
        fun PropsWithClassName.autoSized() = css { width = 1.px; whiteSpace = WhiteSpace.nowrap }
        fun ChildrenBuilder.autoSizedCol() = col { autoSized() }
        component = Paper
        Table {
            colgroup {
                autoSizedCol()
                col {}
                repeat(3) {
                    autoSizedCol()
                }
            }
            TableHead {
                TableRow {
                    TableCell { autoSized() }
                    TableCell { +"Question" }
                    TableCell {
                        autoSized()
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
                    if (showGroupPredCol) TableCell { autoSized(); +"Group pred." }
                    if (showResolutionCol) TableCell { autoSized(); +"Resolution" }
                }
            }
            TableBody {
                props.questions.map {question ->
                    TableRow {
                        TableCell {
                            autoSized()
                            Stack {
                                direction = responsive(StackDirection.row)
                                Tooltip {
                                    title = breakLines(
                                        if (question.visible) "Question is visible to forecasters.\nClick to make it hidden."
                                        else "Question is hidden from forecasters.\nClick to make it visible."
                                    )
                                    arrow = true
                                    span {
                                        IconToggleButton {
                                            on = question.visible
                                            onIcon = VisibilityIcon.create()
                                            offIcon = VisibilityOffOutlinedIcon.create()
                                            onChange = { postEditQuestion(question.id, EditQuestionFieldType.VISIBLE, it) }
                                        }
                                    }
                                }
                                Tooltip {
                                    title = breakLines(
                                        if (question.open) "Question is open for new predictions.\nClick to make it closed."
                                        else "Question is closed (new predictions are not allowed).\nClick to make it open."
                                    )
                                    arrow = true
                                    span {
                                        IconToggleButton {
                                            on = question.open
                                            onIcon = LockOpenIcon.create()
                                            offIcon = LockIcon.create()
                                            onChange = { postEditQuestion(question.id, EditQuestionFieldType.OPEN, it) }
                                        }
                                    }
                                }
                                Tooltip {
                                    title = ReactNode("Edit question")
                                    IconButton {
                                        EditIcon()
                                        onClick = { editQuestionOpen(question) }
                                    }
                                }
                            }
                        }
                        TableCell { +question.name }
                        TableCell {
                            autoSized()
                            +"${question.numPredictors} "
                            +" / "
                            +"${question.numPredictions}"
                        }
                        if (showGroupPredCol)
                            TableCell {
                                autoSized()
                                Tooltip {
                                    arrow = true
                                    title = breakLines(
                                        if (question.groupPredVisible)
                                            "Group prediction is visible to forecasters.\nClick to make it hidden."
                                        else
                                            "Group prediction is hidden from forecasters.\nClick to make it visible."
                                    )
                                    span {
                                        IconToggleButton {
                                            on = question.groupPredVisible
                                            onIcon = VisibilityIcon.create()
                                            offIcon = VisibilityOffOutlinedIcon.create()
                                            onChange = {
                                                postEditQuestion(
                                                    question.id,
                                                    EditQuestionFieldType.GROUP_PRED_VISIBLE,
                                                    it
                                                )
                                            }
                                        }
                                    }
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
                                autoSized()
                                question.resolution?.let {
                                    Tooltip {
                                        arrow = true
                                        title = breakLines(
                                            if (question.resolutionVisible)
                                                "Resolution is visible to forecasters.\nClick to make it hidden."
                                            else
                                                "Resolution is hidden from forecasters.\nClick to make it visible."
                                        )
                                        span {
                                            IconToggleButton {
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
