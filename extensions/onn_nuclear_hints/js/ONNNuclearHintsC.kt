package extensions.onn_nuclear_hints

import components.AppStateContext
import components.redesign.questions.predictions.MyPredictionDescriptionProps
import react.ChildrenBuilder
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.use
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.refs.ref
import tools.confido.state.globalState
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed

object OnnNuclearHintsCE : ClientExtension, ONNNuclearHintsExtension() {
    override fun myPredictionDescriptionExtra(props: MyPredictionDescriptionProps, cb: ChildrenBuilder) {
        val (appState,_) = use(AppStateContext)!!
        val q = props.question ?: return
        val r = q.room ?: return
        if (r.ref != nuclearRoom) return
        val prob = (props.dist as? BinaryDistribution)?.yesProb ?: return
        cb.apply {
            div {
                +"This means you would on average expect nuclear use in this region to occur "
                b {
                    +" in "
                    +(1 / prob).toFixed(1)
                    +" years"
                }
                +" (if the risk remained constant)."
            }
        }
        val otherProbs = r.questions.filter{it.id != q.id}.mapNotNull { (appState.myPredictions[it]?.dist as? BinaryDistribution)?.yesProb }
        val allProbs = listOf(prob) + otherProbs
        if (allProbs.size >= 2) {
            val totalRisk = 1 - allProbs.map { 1 - it }.reduce { a, b -> a * b }
            cb.apply {
                div {
                    +"Together with your other predictions in this room, you expect a total worldwide nuclear use risk of "
                    b {+formatPercent(totalRisk, decimals = 1)}
                    +" (if the regional risks are independent), i.e., nuclear use anywhere in the world on average in "
                    b {+(1/totalRisk).toFixed(1); +" years."}
                }
            }
        }
    }

}