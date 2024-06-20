package extensions

import components.AppStateContext
import components.presenter.PresenterPageProps
import components.presenter.presenterPageMap
import components.redesign.forms.Button
import components.redesign.presenter.PresenterContext
import components.redesign.questions.QuestionContext
import components.redesign.questions.predictions.MyPredictionDescriptionProps
import components.redesign.questions.predictions.yesNoColored
import components.rooms.RoomContext
import components.showError
import csstype.*
import emotion.react.css
import hooks.useCoroutineLock
import hooks.useWebSocket
import payloads.responses.WSData
import payloads.responses.WSError
import payloads.responses.WSLoading
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ul
import react.router.useNavigate
import rooms.Room
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import users.User
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

val MillionaireScoreboardPP = FC<PresenterPageProps<MillionaireScoreboardPV>> { props->
    val room = props.view.room
    val scoreboard = useWebSocket<Map<Ref<User>, Double>>("/api${Room.urlPrefix(room.id)}/ext/millionaire/scoreboard.ws")
    if (scoreboard !is WSData) return@FC
    ReactHTML.div {
        css {
            margin = 20.px
            height = 90.vh
            columnFill = Auto.auto
            columnCount = integer(2)
        }
        ReactHTML.table {
            css {
                fontSize = 32.px
                borderCollapse = BorderCollapse.collapse
                "tr" {
                    borderTop = Border(1.px, LineStyle.solid, NamedColor.black)
                    borderBottom = Border(1.px, LineStyle.solid, NamedColor.black)
                }
            }
            scoreboard.data.entries.sortedBy { -it.value }.forEach {
                ReactHTML.tr {
                    ReactHTML.td {
                        +(it.key.deref()?.nick ?: "(Anonymous)")
                    }
                    ReactHTML.td {
                        css { paddingLeft = 32.px }
                        +"$ ${it.value.roundToInt()}"
                    }
                }
            }
        }
    }
}

object MillionaireCE : ClientExtension, MillionaireExt() {
    override fun registerPresenterPages() = mapOf(presenterPageMap(MillionaireScoreboardPP))

    override fun questionPageExtra(q: Question, place: ClientExtension.QuestionPagePlace, cb: ChildrenBuilder) {
        useContext(AppStateContext)
        val room = useContext(RoomContext)
        val nav = useNavigate()
        val qs = questions(room)
        val st = getState(room)
        // Automatically move to next question
        useEffect(q.id, q.state, st.curQuestion?.id) {
            if (q in qs && q.state == QuestionState.RESOLVED && q.id != st.curQuestion?.id && st.curQuestion?.id != null)
                nav(room.urlPrefix + st.curQuestion.urlPrefix)
        }
    }

    override fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {
        useContext(AppStateContext)
        val qs = questions(room)
        val presenterCtl = useContext(PresenterContext)
        val st = getState(room)
        val lk = useCoroutineLock()

        if (qs.isEmpty()) return


        fun setState(newState: MillionaireState) {
            lk {
                Client.sendData("/api${room.urlPrefix}/ext/millionaire/state", newState, onError = { showError(it) }) {}
            }
        }
        cb.apply {
            h2 { +"Millionaire" }

            qs.filter { it.resolution == null }.let { noRes->
                if (!noRes.isEmpty()) {
                    b{
                        css { color = NamedColor.red }
                        +"ERROR: The following questions are missing resolution:"
                    }
                    ul {
                        noRes.forEach { li { +it.name } }
                    }
                    return
                }
            }
            qs.filter { it.answerSpace !is BinarySpace }.let { nonBin ->
                if (!nonBin.isEmpty()) {
                    b{
                        css { color = NamedColor.red }
                        +"ERROR: The following questions have non-binary answer spaces:"
                    }
                    ul {
                        nonBin.forEach { li { +it.name } }
                    }
                    return
                }
            }

            div { b{+"Current state: "}; +st.type.toString() }
            div { b{+"Current question number: "}; +(st.curIndex + 1).toString() }
            div { b{+"Current question: "}; +(st.curQuestion?.name ?: "") }
            div { b{+"Next question: "}; +(qs.getOrNull(st.curIndex+1)?.name ?: "") }

            if (st.type == MillionaireStateType.BEFORE_START)
                Button {
                    +"Start game"
                    onClick = { setState(MillionaireState(MillionaireStateType.ASKING, 0, null)) }
                }
            else if (st.type == MillionaireStateType.ASKING) {
                Button {
                    +"Close question"
                    onClick = { setState(MillionaireState(MillionaireStateType.RESOLVED, st.curIndex, null)) }
                }
            }
            else if (st.type == MillionaireStateType.RESOLVED) {
                if (st.curIndex < qs.size - 1)
                Button {
                    +"Next question"
                    onClick = { setState(MillionaireState(MillionaireStateType.ASKING, st.curIndex+1, null)) }
                }
            }

            Button {
                +"Open presenter"
                onClick = { presenterCtl.offer(st.presenterView(room)) }
            }
            Button {
                +"Reset to start"
                onClick = { setState(MillionaireState(MillionaireStateType.BEFORE_START, -1, null))}
            }

            h3{+"Scoreboard"}
            MillionaireScoreboardPP {
                view = MillionaireScoreboardPV(room.ref)
            }
        }
    }

    override fun myPredictionDescriptionExtra(props: MyPredictionDescriptionProps, cb: ChildrenBuilder) {
        val room = useContext(RoomContext)
        val q = useContext(QuestionContext) ?: return
        val (appState,_) = useContext(AppStateContext)
        val qs = questions(room)
        val qids = qs.map {it.id}
        //val myScoreWS = useWebSocket<Double?>("/api${room.urlPrefix}/ext/millionaire/my_score.ws")
        //if (myScoreWS is WSLoading) cb.apply { div {+"load"} }
        //if (myScoreWS is WSError) cb.apply { div {+"err"} }
        //if (myScoreWS !is WSData) return
        //val myScore = myScoreWS.data ?: INITIAL_SCORE
        if (q.id !in qids) return
        if (q.answerSpace !is BinarySpace) return
        val dist = props.dist
        if (dist !is BinaryDistribution) return
        val likelyOutcome = dist.yesProb >= 0.5
        val resolvedQuestions = qs.takeWhile { it.state == QuestionState.RESOLVED }
        val scoreCoef = resolvedQuestions.map { rq-> computeScore(appState.myPredictions[rq.ref], rq.resolution!!) ?: 1.0 }
        val scoreAfterQ = scoreCoef.runningFold(INITIAL_SCORE) { a,b -> a*b }
        val diffScores = scoreAfterQ.zipWithNext { a, b -> b-a }
        cb.apply {
            if (q.resolved) {
                val idx = resolvedQuestions.map{it.id}.indexOf(q.id)
                console.log("RES IDX $idx")
                if (idx >= 0 && idx < diffScores.size) {
                    val dsc = diffScores[idx]
                    val word = if (dsc >= 0) "gained" else "lost"
                    div { +"You $word \$ ${dsc.absoluteValue.roundToInt()} on this answer." }
                    div { +"Your score after this answer is \$ ${scoreAfterQ[idx+1].absoluteValue.roundToInt()}." }
                }
            } else {
                val myScore = scoreAfterQ.last()
                div { +"Your current score is $ ${myScore.roundToInt()}" }
                listOf(likelyOutcome, !likelyOutcome).forEach { ans ->
                    val newScore = myScore * computeScore(dist.yesProb, ans)
                    val diffScore = newScore - myScore
                    val word = if (diffScore >= 0) "gain" else "lose"
                    div {
                        +"If the correct answer is "
                        yesNoColored(ans)
                        +" will "
                        b{+word}
                        +" \$ ${diffScore.absoluteValue.roundToInt()} (ending up with \$ ${newScore.roundToInt()})"
                    }
                }
            }
        }
    }
}