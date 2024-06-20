package extensions

import components.redesign.basic.palette
import components.redesign.forms.*
import components.redesign.questions.ChipCSS
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.showError
import csstype.Border
import csstype.LineStyle
import csstype.NamedColor
import csstype.px
import emotion.react.css
import kotlinx.coroutines.coroutineScope
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionState
import react.ChildrenBuilder
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ul
import rooms.Room
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.extensions.get
import tools.confido.extensions.with
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.utils.capFirst
import tools.confido.utils.forEachDeref
import utils.runCoroutine

object QuestionGroupsCE: ClientExtension, QuestionGroupsExtension() {
    override fun editQuestionDialogExtra(props: EditQuestionDialogProps, cb: ChildrenBuilder) {
        var rawGroups by useContextState(ExtensionContextPlace.EDIT_QUESTION_DIALOG, "groups", props.entity?.extensionData?.get(
            QuestionGroupsKey)?.joinToString(",") ?: "")
        cb.apply {
            FormSection {
                this.title = "Question groups"
                FormField {
                    title = "Groups"
                    comment = "comma separated"
                    TextInput {
                        value = rawGroups
                        onChange = { rawGroups = it.target.value }
                    }
                }
            }
        }
    }

    override fun assembleQuestion(q: Question, states: Map<String, dynamic>): Question {
        val rawGroups = states["groups"] as? String ?: ""
        val groups = if (rawGroups.isEmpty()) emptySet() else rawGroups.split(",").toSet()
        return q.copy(extensionData = q.extensionData.with(QuestionGroupsKey, groups))
    }
    override fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {
        val grouped = mutableMapOf<String, MutableList<Question>>()
        room.questions.reversed().forEachDeref { q->
            q.extensionData[QuestionGroupsKey].forEach { g-> grouped.getOrPut(g) {mutableListOf()}.add(q) }
        }
        cb.apply {
            grouped.forEach { (g, qs) ->
                h3 { +g }
                div{
                    +"Set group to: "

                    QuestionState.entries.forEach { newState->
                        ReactHTML.button {
                            css(ChipCSS) {
                                border = Border(1.px, LineStyle.solid, newState.palette.color)
                                color = newState.palette.color
                                backgroundColor = NamedColor.white
                            }
                            +newState.name.lowercase().capFirst()
                            onClick = {
                                runCoroutine {
                                    Client.sendData(
                                        "/api/rooms/${room.id}/groups/${g}/set_state",
                                        newState,
                                        onError = {},
                                        block = {})
                                }
                            }
                        }
                    }
                }
                ul {
                    qs.forEach { q-> li { +q.name } }
                }
            }
        }
    }
}