package extensions.prediction_showcase

import tools.confido.web.components.presenter.PresenterPageProps
import tools.confido.web.components.presenter.presenterPageMap
import tools.confido.web.components.redesign.PresenterIcon
import tools.confido.web.components.redesign.basic.Stack
import tools.confido.web.components.redesign.basic.rowStyleTableCSS
import tools.confido.web.components.redesign.forms.IconButton
import tools.confido.web.components.redesign.presenter.PresenterContext
import web.cssom.*
import emotion.react.css
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tools.confido.rooms.Room
import tools.confido.distributions.*
import tools.confido.web.components.redesign.forms.QuestionPicker
import tools.confido.web.components.redesign.forms.Select
import tools.confido.web.components.redesign.rooms.RoomContext
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionDataKeyWithDefault
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.refs.eqid
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.NumericSpace
import tools.confido.web.utils.questionUrl
import extensions.shared.ValueWithUser
import tools.confido.refs.Ref
import tools.confido.utils.formatPercent
import tools.confido.state.Topics
import tools.confido.web.components.QuestionPredictors
import tools.confido.web.components.useQuestion
import tools.confido.web.components.useRooms

val PredictionShowcasePP = FC<PresenterPageProps<PredictionShowcasePV>> { props ->
    val view = props.view
    val question = useQuestion(view.question)
    val predictions = useSubscription(question?.let { showcasePredictionsTopic(it.ref) })
    if (question == null || predictions !is WSData) return@FC

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
            +question.name
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
                    th { +"Prediction" }
                }
            }
            tbody {
                predictions.data.forEach { pred ->
                    tr {
                        css {
                            if (pred.isSpecial) {
                                fontWeight = FontWeight.bold
                                backgroundColor = Color("#e3f2fd")
                            }
                        }
                        td { +pred.nickname }
                        td {
                            // Format based on question type
                            when (question.answerSpace) {
                                is BinarySpace -> +formatPercent(pred.value)
                                is NumericSpace -> +(question.answerSpace).formatValue(pred.value)
                                else -> +pred.value.toString()
                            }
                        }
                    }
                }
            }
        }
    }
}

val GroupPredictionComparePP = FC<PresenterPageProps<GroupPredictionComparePV>> { props ->
    val view = props.view
    val room = useRooms()[view.room.id]
    val qmap = (useSubscription(room?.let { Topics.roomQuestions(it.ref) }) as? WSData)?.data ?: emptyMap()
    val groupPreds = useSubscription(room?.let { Topics.roomGroupPred(it.ref) })
    if (room == null || groupPreds !is WSData) return@FC

    val questions = room.questions.mapNotNull { qmap[it.id] }.filter { q ->
        view.questionGroup?.let { group ->
            group in q.tags
        } ?: true
    }
    val data = groupPreds.data

    Stack {
        css {
            alignItems = AlignItems.center
            maxWidth = 100.vw
            width = 100.vw
            maxHeight = 100.vh
            height = 100.vh
            padding = 20.px
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
                    th { +"Question" }
                    th { +"Group Prediction" }
                    th { +"# Forecasters" }
                }
            }
            val vals =  questions.mapNotNull { q->
                val pred = data[q.ref]?.dist
                q to when {
                    pred == null -> return@mapNotNull null
                    q.answerSpace is BinarySpace ->   (pred as BinaryDistribution).yesProb
                    q.answerSpace is NumericSpace ->  (pred as ContinuousProbabilityDistribution).mean
                    else ->  return@mapNotNull null
                }
            }.sortedByDescending { it.second }
            tbody {
                vals.forEach { (q,v) ->
                    tr {
                        td { +q.name }
                        td {
                            when (q.answerSpace) {
                                is BinarySpace -> +formatPercent(v)
                                is NumericSpace -> +q.answerSpace.formatValue(v)
                            }
                        }
                        td { QuestionPredictors { this.question = q.ref } }
                    }
                }
            }
        }
    }
}

private val PredictionShowcasePVEditorComponent = FC<tools.confido.extensions.FlowItemEditorProps> { props ->
    val questions = tools.confido.web.components.redesign.presentation.useRoomQuestions()
    val view = (props.action as? tools.confido.state.SetPresenterViewAction)?.view as? PredictionShowcasePV ?: return@FC
    val onViewChange: (tools.confido.state.PresenterView) -> Unit = { v -> props.onChange(tools.confido.state.SetPresenterViewAction(v)) }
    tools.confido.web.components.redesign.presentation.FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = view.question.id
        onChange = { onViewChange(PredictionShowcasePV(Ref(it))) }
    }
}

private val GroupPredComparePVEditorComponent = FC<tools.confido.extensions.FlowItemEditorProps> { props ->
    val allTags0 = tools.confido.web.components.redesign.presentation.useRoomQuestions()
    val view = (props.action as? tools.confido.state.SetPresenterViewAction)?.view as? GroupPredictionComparePV ?: return@FC
    val onViewChange: (tools.confido.state.PresenterView) -> Unit = { v -> props.onChange(tools.confido.state.SetPresenterViewAction(v)) }
    val allTags = allTags0
        .flatMap { it.tags }.distinct().sorted()
    tools.confido.web.components.redesign.presentation.FieldLabel { +"Filter by tag (optional)" }
    Select {
        value = view.questionGroup ?: ""
        onChange = { e ->
            val tag = e.target.value
            onViewChange(view.copy(questionGroup = tag.ifEmpty { null }))
        }
        css { width = 100.pct }
        react.dom.html.ReactHTML.option { value = ""; +"All questions" }
        allTags.forEach { tag ->
            react.dom.html.ReactHTML.option { value = tag; +tag }
        }
    }
}

object PredictionShowcaseCE : ClientExtension, PredictionShowcaseExtension() {
    override fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {
        val presenterCtl = use(PresenterContext)!!
        val qmap = (useSubscription(Topics.roomQuestions(room.ref)) as? WSData)?.data ?: emptyMap()

        cb.apply {
            h2 {+"Prediction showcase"}
            table {
                css(ClassName("qmgmt-tab"), rowStyleTableCSS) {
                }
                thead {
                    tr {
                        th { +"Question" }
                        th { +"# Predictors" }
                        th { +"Actions" }
                    }
                }
                tbody {
                    room.questions.reversed().forEach { qRef ->
                        val q = qmap[qRef.id] ?: return@forEach
                        tr {
                            td { +q.name }
                            td { QuestionPredictors { this.question = q.ref } }
                            td {
                                IconButton {
                                    PresenterIcon {}
                                    onClick = {
                                        presenterCtl.offer(PredictionShowcasePV(q.ref))
                                    }
                                }
                                IconButton {
                                    PresenterIcon{}
                                    onClick = {
                                        presenterCtl.offer(GroupPredictionComparePV(room.ref))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun registerPresenterPages() =
        mapOf(
            presenterPageMap(PredictionShowcasePP),
            presenterPageMap(GroupPredictionComparePP)
        )

    override fun registerFlowItemTypes() = listOf(
        tools.confido.extensions.FlowItemTypeEntry(
            key = "predictionShowcase",
            label = "Individual predictions showcase",
            category = tools.confido.extensions.FlowItemCategory.VIEW,
            createDefault = { tools.confido.state.SetPresenterViewAction(PredictionShowcasePV(Ref(""))) },
            editor = PredictionShowcasePVEditorComponent,
            matches = { it is tools.confido.state.SetPresenterViewAction && it.view is PredictionShowcasePV },
        ),
        tools.confido.extensions.FlowItemTypeEntry(
            key = "groupPredCompare",
            label = "Compare group predictions",
            category = tools.confido.extensions.FlowItemCategory.VIEW,
            createDefault = { tools.confido.state.SetPresenterViewAction(GroupPredictionComparePV(Ref(""))) },
            editor = GroupPredComparePVEditorComponent,
            matches = { it is tools.confido.state.SetPresenterViewAction && it.view is GroupPredictionComparePV },
        ),
    )
}
