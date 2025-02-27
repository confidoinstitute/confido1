package extensions

import components.presenter.PresenterPageProps
import components.presenter.presenterPageMap
import components.redesign.basic.Stack
import components.redesign.basic.rowStyleTableCSS
import components.redesign.forms.*
import components.redesign.presenter.PresenterContext
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.redesign.forms.Button
import csstype.*
import emotion.react.css
import hooks.useWebSocket
import payloads.responses.WSData
import tools.confido.question.Prediction
import icons.ProjectorScreenOutlineIcon
import react.*
import react.dom.html.InputType
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import rooms.Room
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.extensions.get
import tools.confido.extensions.with
import tools.confido.question.Question
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.utils.toFixed
import utils.questionUrl
import utils.roomUrl
import extensions.shared.ValueWithUser

val ReferenceForcastScoreboardPP = FC<PresenterPageProps<ReferenceForcastScoreboardPV>> { props ->
    val room = props.view.room.deref() ?: return@FC
    val scores = useWebSocket<List<UserScore>>("/api${roomUrl(room.id)}/ext/reference_forecast/scoreboard.ws")
    if (scores !is WSData) return@FC

    Stack {
        css {
            alignItems = AlignItems.center
            maxWidth = 100.vw
            width = 100.vw
            maxHeight = 100.vh
            height = 100.vh
            padding = 20.px
        }

        h1 {
            css {
                fontSize = 52.px
            }
            +"Scoreboard"
        }

        table {
            css {
                fontSize = 32.px
                borderCollapse = BorderCollapse.collapse
                "tr" {
                    borderTop = Border(1.px, LineStyle.solid, NamedColor.black)
                    borderBottom = Border(1.px, LineStyle.solid, NamedColor.black)
                }
                "th, td" {
                    padding = Padding(8.px, 16.px)
                }
                "th" {
                    textAlign = TextAlign.left
                }
            }
            thead {
                tr {
                    th { +"User" }
                    th { +"Score" }
                }
            }
            tbody {
                scores.data.forEach { score ->
                    tr {
                        css {
                            if (score.isSpecial) {
                                fontWeight = FontWeight.bold
                                backgroundColor = Color("#e3f2fd")
                            }
                        }
                        td { +score.nickname }
                        td { +"${score.score.toFixed(1)}" }
                    }
                }
            }
        }
    }
}

object ReferenceForecastCE : ClientExtension, ReferenceForecastExtension() {
    override fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {
        val presenterCtl = useContext(PresenterContext)
        val groupPredsWS = useWebSocket<Map<String, Prediction?>>("/state${room.urlPrefix}/group_pred")
        val groupPreds = groupPredsWS.data ?: emptyMap()


        // Show scoreboard button if there are any questions with reference forecasts
        if (room.questions.any { qRef -> qRef.deref()?.extensionData?.get(ReferenceForcastKey) != null }) {
            cb.apply {
                Button {
                    +"Show Reference Forecast Scoreboard"
                    onClick = {
                        presenterCtl.offer(ReferenceForcastScoreboardPV(room.ref))
                    }
                }
            }
        }
    }

    override fun editQuestionDialogExtra(props: EditQuestionDialogProps, cb: ChildrenBuilder) {
        if (props.entity?.answerSpace !is BinarySpace) return

        var referenceForecast by useContextState(
            ExtensionContextPlace.EDIT_QUESTION_DIALOG,
            "reference_forecast",
            props.entity?.extensionData?.get(ReferenceForcastKey)?.let {it*100.0}?.toString() ?: ""
        )

        cb.apply {
            FormSection {
                title = "Reference Forecast"
                FormField {
                    title = "Reference probability"
                    comment = "External reference forecast (e.g. from Metaculus)"
                    TextInput {
                        value = referenceForecast
                        onChange = { referenceForecast = it.target.value }
                        placeholder = "Enter probability (0-100)"
                        type = InputType.number
                        min = "0"
                        max = "100"
                    }
                }
            }
        }
    }

    override fun assembleQuestion(q: Question, states: Map<String, dynamic>): Question {
        if (q.answerSpace !is BinarySpace) return q

        val refForecast = states["reference_forecast"] as? String
        val probability = refForecast?.toDoubleOrNull()?.div(100)
        return q.copy(extensionData = q.extensionData.with(ReferenceForcastKey, probability))
    }

    override fun registerPresenterPages() =
        mapOf(
            presenterPageMap(ReferenceForcastScoreboardPP)
        )
}
