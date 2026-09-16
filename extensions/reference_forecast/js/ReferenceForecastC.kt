package tools.confido.extensions.reference_forecast

import tools.confido.web.components.presenter.PresenterPageProps
import tools.confido.web.components.presenter.presenterPageMap
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.basic.rowStyleTableCSS
import tools.confido.web.components.basic.sansSerif
import tools.confido.web.components.forms.*
import tools.confido.web.components.presenter.PresenterContext
import tools.confido.web.components.questions.dialog.EditQuestionDialogProps
import tools.confido.web.components.forms.Button
import tools.confido.web.components.presentation.pvFlowEditor
import tools.confido.web.components.presentation.roomSubject
import web.cssom.*
import emotion.react.css
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import tools.confido.question.Prediction
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tools.confido.rooms.Room
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.extensions.get
import tools.confido.extensions.with
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.utils.toFixed
import tools.confido.web.utils.questionUrl
import tools.confido.web.utils.roomUrl
import tools.confido.extensions.shared.ValueWithUser
import web.html.InputType
import web.html.number
import tools.confido.state.Topics
import tools.confido.web.components.useRooms

val ReferenceForcastScoreboardPP = FC<PresenterPageProps<ReferenceForcastScoreboardPV>> { props ->
    val room = useRooms()[props.view.room.id]
    val scores = useSubscription(room?.let { referenceScoreboardTopic(it.ref) })
    if (room == null || scores !is WSData) return@FC

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
        val presenterCtl = use(PresenterContext)!!
        val groupPredsWS = useSubscription(Topics.roomGroupPred(room.ref))
        val groupPreds = groupPredsWS.data ?: emptyMap()
        val qmap = (useSubscription(Topics.roomQuestions(room.ref)) as? WSData)?.data ?: emptyMap()


        // Show scoreboard button if there are any questions with reference forecasts
        if (room.questions.any { qRef -> qmap[qRef.id]?.extensionData?.get(ReferenceForcastKey) != null }) {
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

    override fun registerFlowItemTypes() = listOf(
        tools.confido.extensions.FlowItemTypeEntry(
            key = "refForecastScoreboard",
            label = "Reference forecast scoreboard",
            category = tools.confido.extensions.FlowItemCategory.VIEW,
            createDefault = { tools.confido.state.SetPresenterViewAction(ReferenceForcastScoreboardPV(tools.confido.refs.Ref(""))) },
            // roomSubject backfills the room ref from the edited room (the old
            // info-only editor never did, leaving Ref("") → blank projector).
            editor = pvFlowEditor(roomSubject<ReferenceForcastScoreboardPV>(
                hint = "Will show the reference forecast scoreboard for this room.",
                get = { it.room }, set = { v, r -> v.copy(room = r.ref) })),
            matches = { it is tools.confido.state.SetPresenterViewAction && it.view is ReferenceForcastScoreboardPV },
        )
    )
}
