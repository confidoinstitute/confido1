package tools.confido.extensions.prediction_showcase

import tools.confido.web.components.presenter.PresenterControlsProps
import tools.confido.web.components.presenter.PresenterPageProps
import tools.confido.web.components.presenter.presenterControlsMap
import tools.confido.web.components.presenter.presenterPageMap
import tools.confido.web.components.redesign.forms.FormField
import tools.confido.web.components.redesign.presentation.pvFlowEditor
import tools.confido.web.components.redesign.presentation.questionSubject
import tools.confido.web.components.redesign.presentation.roomSubject
import react.dom.html.ReactHTML.option
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
import tools.confido.rooms.AttributionLevel
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
import tools.confido.extensions.shared.ValueWithUser
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
                // Safe casts: the space no longer implies the dist type (a quantile
                // summary can ride a NumericSpace question) — skip what we can't read.
                q to when {
                    pred == null -> return@mapNotNull null
                    q.answerSpace is BinarySpace ->
                        (pred as? BinaryDistribution)?.yesProb ?: return@mapNotNull null
                    q.answerSpace is NumericSpace ->
                        (pred as? ContinuousProbabilityDistribution)?.mean ?: return@mapNotNull null
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

/** Shared control for the group-prediction compare view: the tag filter. Serves
 *  both the live control panel and the flow editor (which backfills the room ref
 *  via roomSubject — the old flow-only editor never did, leaving Ref("")). */
private val GroupPredictionComparePC = FC<PresenterControlsProps<GroupPredictionComparePV>> { props ->
    val roomRef = props.view.room.takeIf { it.id.isNotBlank() }
    val qmap = (useSubscription(roomRef?.let { Topics.roomQuestions(it) }) as? WSData)?.data ?: emptyMap()
    val allTags = qmap.values.flatMap { it.tags }.distinct().sorted()
    FormField {
        title = "Filter by tag"
        Select {
            value = props.view.questionGroup ?: ""
            disabled = props.disabled
            onChange = { e ->
                props.onChange(props.view.copy(questionGroup = e.target.value.ifEmpty { null }))
            }
            css { width = 100.pct }
            option { value = ""; +"All questions" }
            allTags.forEach { tag -> option { value = tag; +tag } }
            // Keep an already-selected tag visible even when currently unknown.
            props.view.questionGroup?.takeIf { it !in allTags }?.let { option { value = it; +it } }
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
                        // No showcase for unattributed questions (individual predictions).
                        if (q.effectiveAttributionIn(room).level >= AttributionLevel.ANONYMOUS) return@forEach
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

    override fun registerPresenterControls() = mapOf(
        presenterControlsMap(GroupPredictionComparePC),
    )

    override fun registerFlowItemTypes() = listOf(
        tools.confido.extensions.FlowItemTypeEntry(
            key = "predictionShowcase",
            label = "Individual predictions showcase",
            category = tools.confido.extensions.FlowItemCategory.VIEW,
            createDefault = { tools.confido.state.SetPresenterViewAction(PredictionShowcasePV(Ref(""))) },
            // Unattributed questions cannot be showcased (individual predictions, server-enforced).
            editor = pvFlowEditor(questionSubject<PredictionShowcasePV>(
                filter = { q, room -> q.effectiveAttributionIn(room).level < AttributionLevel.ANONYMOUS },
                get = { it.question }, set = { v, q -> v.copy(question = q) })),
            matches = { it is tools.confido.state.SetPresenterViewAction && it.view is PredictionShowcasePV },
        ),
        tools.confido.extensions.FlowItemTypeEntry(
            key = "groupPredCompare",
            label = "Compare group predictions",
            category = tools.confido.extensions.FlowItemCategory.VIEW,
            createDefault = { tools.confido.state.SetPresenterViewAction(GroupPredictionComparePV(Ref(""))) },
            editor = pvFlowEditor(roomSubject<GroupPredictionComparePV>(
                get = { it.room }, set = { v, r -> v.copy(room = r.ref) })),
            matches = { it is tools.confido.state.SetPresenterViewAction && it.view is GroupPredictionComparePV },
        ),
    )
}
