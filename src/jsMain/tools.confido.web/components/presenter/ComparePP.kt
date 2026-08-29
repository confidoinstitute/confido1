package tools.confido.web.components.presenter

import emotion.react.css
import js.objects.unsafeJso as jso
import react.FC
import react.dom.html.ReactHTML.div
import tools.confido.api.Api
import tools.confido.payloads.responses.UserPredCompareRequest
import tools.confido.payloads.responses.UserPredCompareRow
import tools.confido.payloads.responses.WSData
import tools.confido.refs.ref
import tools.confido.state.*
import tools.confido.utils.List2
import tools.confido.web.ApiError
import tools.confido.web.Client
import tools.confido.web.components.redesign.basic.Stack
import tools.confido.web.components.redesign.basic.sansSerif
import tools.confido.web.components.redesign.questions.predictions.CompareChangesChart
import tools.confido.web.components.redesign.questions.predictions.CompareDistributions
import tools.confido.web.components.redesign.questions.predictions.CompareScatterChart
import tools.confido.web.components.redesign.questions.predictions.formatUpdateDateTime
import tools.confido.web.components.useQuestion
import tools.confido.web.hooks.useSubscription
import tools.confido.web.hooks.useSuspendResult
import web.cssom.*

private const val ZOOM = 1.8

/**
 * Presenter rendering of the two-moment comparison. Timepoint references
 * resolve at render: markers by name against the (live-synced) room + question
 * marks, "now" against the live group prediction — so a flow-created marker
 * lights the slide up as soon as it exists.
 */
val ComparePP = FC<PresenterPageProps<ComparePV>> { props ->
    val v = props.view
    // Hooks precede the data-dependent early returns (React #310).
    val question = useQuestion(v.question)
    val rooms = (useSubscription(Topics.rooms()) as? WSData)?.data
    val room = rooms?.values?.firstOrNull { v.question in it.questions }
    val nowPred = (useSubscription(question?.let { Topics.groupPred(it.ref) }) as? WSData)?.data
    val allMarks = (room?.timelineMarks ?: emptyList()) + (question?.timelineMarks ?: emptyList())

    fun resolve(tp: TimepointRef): Int? = when (tp) {
        is NowTP -> null
        is AtTP -> tp.ts
        is MarkTP -> allMarks.firstOrNull { it.name == tp.name }?.ts
    }
    // Older side left, "now" always newest; unresolvable marker ranks last.
    val ordered = List2(v.tp1, v.tp2).sortedBy { resolve(it)?.toLong() ?: Long.MAX_VALUE }
    val tsA = resolve(ordered.e1)
    val tsB = resolve(ordered.e2)

    val fetched = useSuspendResult(question?.id, tsA, tsB) {
        val q = question ?: return@useSuspendResult null
        List2(tsA, tsB).map { ts ->
            ts?.let {
                try { Client.call(Api.questionGroupPredAt(q.ref, it))?.dist } catch (e: ApiError) { null }
            }
        }
    }
    // Per-user rows for the individual modes; re-fetched when a new prediction
    // lands (nowPred is the live head). Null on missing permission.
    val rows: List<UserPredCompareRow>? = useSuspendResult(question?.id, tsA, tsB, v.mode, nowPred?.ts) {
        val q = question ?: return@useSuspendResult null
        if (v.mode == CompareViewMode.DISTRIBUTIONS) null
        else try {
            Client.call(Api.questionUserPredCompare(q.ref), UserPredCompareRequest(tsA, tsB))
        } catch (e: ApiError) { null }
    }
    if (question == null) return@FC

    fun label(tp: TimepointRef, ts: Int?): String = when {
        tp is MarkTP && ts == null -> "${tp.name} (not yet)"
        ts == null -> "now"
        else -> {
            val name = allMarks.firstOrNull { it.ts == ts }?.name
            val dt = formatUpdateDateTime(ts)
            if (name != null) "$name · $dt" else dt
        }
    }
    fun distOf(ts: Int?, idx: Int) = if (ts == null) nowPred?.dist else fetched?.get(idx)

    Stack {
        css {
            gap = 30.pt
            width = 100.vw
            height = 100.vh
            padding = 2.5.vh
            alignItems = AlignItems.center
            justifyContent = JustifyContent.flexStart
            fontFamily = sansSerif
        }
        div {
            css {
                textAlign = TextAlign.center
                fontWeight = integer(600)
                fontSize = 7.vh
            }
            +question.name
        }
        div {
            // CSS zoom scales the content inside the box (the box itself keeps its
            // percentage width) — see TimelinePP.
            style = jso { asDynamic().zoom = ZOOM }
            css { width = 100.pct }
            when (v.mode) {
                CompareViewMode.DISTRIBUTIONS -> CompareDistributions {
                    space = question.answerSpace
                    this.question = question
                    distA = distOf(tsA, 0)
                    labelA = label(ordered.e1, tsA)
                    this.tsA = tsA
                    distB = distOf(tsB, 1)
                    labelB = label(ordered.e2, tsB)
                    this.tsB = tsB
                }
                CompareViewMode.CHANGES, CompareViewMode.SCATTER -> {
                    if (rows == null) div {
                        css { textAlign = TextAlign.center; color = Color("#999999"); fontSize = 18.px }
                        +"Individual predictions are not available."
                    } else {
                        val labels = List2(label(ordered.e1, tsA), label(ordered.e2, tsB))
                        if (v.mode == CompareViewMode.CHANGES) CompareChangesChart {
                            this.rows = rows
                            this.labels = labels
                            showNames = v.showNames
                        } else CompareScatterChart {
                            this.rows = rows
                            this.labels = labels
                            showNames = v.showNames
                        }
                    }
                }
            }
        }
    }
}
