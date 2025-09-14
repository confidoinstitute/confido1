package extensions

import components.AppStateContext
import components.redesign.forms.FormField
import components.redesign.forms.FormSection
import components.redesign.forms.TextInput
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.redesign.questions.predictions.BinaryPredInputProps
import components.redesign.questions.predictions.MyPredictionDescriptionProps
import web.cssom.NamedColor
import web.cssom.Position
import web.cssom.integer
import web.cssom.px
import emotion.react.css
import js.objects.unsafeJso as jso
import react.ChildrenBuilder
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.router.dom.Link
import react.use
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.extensions.get
import tools.confido.extensions.withOrWithout
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.globalState
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed
import utils.panzoom1d.PZState
import web.window.WindowTarget
import web.window._blank

object ConditionalBaselineCE: ClientExtension, ConditionalBaselineExtension() {
    val qidRE = Regex("/questions/([a-zA-Z0-9]+)(?:$|/.*)")
    val baselineColor = NamedColor.darkcyan
    fun extractBaseline(rawID: String?) = rawID?.ifBlank { null }
        ?.let {
            qidRE.find(it)?.let { m ->
                m.groups[1]?.value
            } ?: it
        }?.let {
            if (it in globalState.questions) {
                Ref<Question>(it)
            } else {
                println("Baseline question '$it' not found, resetting.")
                null
            }
        }
    override fun editQuestionDialogExtra(props: EditQuestionDialogProps, cb: ChildrenBuilder) {
        var rawID by useContextState(ExtensionContextPlace.EDIT_QUESTION_DIALOG, "baseline_question", props.entity?.extensionData?.get(
            BaselineQuestionKey)?.id?:"")
        val baselineRef = extractBaseline(rawID)
        val baselineText = baselineRef?.let { globalState.questions[it.id] }?.name
        val comment = when {
                rawID == "" -> "paste question ID or URL"
                baselineText == null -> "question not found"
                else -> baselineText
            }
        cb.apply {
            FormSection {
                this.title = "Conditional baseline"
                FormField {
                    title = "Baseline question"
                    this.comment = comment
                    TextInput {
                        value = rawID
                        onChange = { rawID = it.target.value }
                    }
                }
            }
        }
    }

    override fun assembleQuestion(q: Question, states: Map<String, dynamic>): Question {
        val baselineRef = extractBaseline(states["baseline_question"] as? String)
        return q.copy(extensionData = q.extensionData.withOrWithout(BaselineQuestionKey, baselineRef))
    }

    override fun myPredictionDescriptionExtra(props: MyPredictionDescriptionProps, cb: ChildrenBuilder) {
        val (appState,_) = use(AppStateContext)!!
        val q = props.question ?: return
        val myPred = (appState.myPredictions[q.ref]?.dist as? BinaryDistribution)?.yesProb
        val baselineRef = q.extensionData.get(BaselineQuestionKey) ?: return
        val baselineQ = appState.questions[baselineRef.id] ?: return
        val baselinePred =  (appState.myPredictions[baselineRef]?.dist as? BinaryDistribution)?.yesProb ?: return
        val rr = myPred ?.let {myPred / baselinePred}
        cb.apply  {
            div {
                +"Your baseline prediction ("
                Link {
                    to = "/rooms/${baselineQ.room?.id}/questions/${baselineRef.id}"
                    css {
                        color = NamedColor.darkgray
                    }
                    target = WindowTarget._blank
                    i { +baselineQ.name }
                }
                + ") is "
                b {
                    css {
                        color = baselineColor
                    }
                    +formatPercent(baselinePred) }
                +"."
                var word = "than"
                rr?.let {
                    +" This means you see the conditional outcome as "
                    b {
                        if (rr <= 1.01 && rr >= 0.99) {
                            +"approximately the same"
                            word = "as"
                        } else if (rr >= 1) {
                            if (rr <= 1.9) {
                                +formatPercent(rr -1 )
                                +" more likely"
                            } else {
                                +rr.toFixed(1)
                                +"× more likely"
                            }
                        } else {
                            if (1/rr > 1.9) {
                                +(1 / rr).toFixed(1)
                                +"× less likely"
                            } else {
                                +formatPercent(1 - rr)
                                +" less likely"
                            }
                        }
                    }
                    +" $word the baseline."
                }
            }
        }
    }

    override fun binarySliderExtra(props: BinaryPredInputProps, zoom: PZState?, cb: ChildrenBuilder) {
        val (appState,_) = use(AppStateContext)!!
        val q = props.question ?: return
        zoom ?: return
        val baselineRef = q.extensionData.get(BaselineQuestionKey) ?: return
        val baselineQ = appState.questions[baselineRef.id] ?: return
        val baselinePred =  (appState.myPredictions[baselineRef]?.dist as? BinaryDistribution)?.yesProb ?: return
        cb.apply {
            div {
                style = jso {
                    left = (zoom.contentToViewport(baselinePred*100.0) - 1.0).px
                }
                css {
                    width = 2.px
                    top = 0.px
                    bottom = 0.px
                    backgroundColor = baselineColor
                    position = Position.absolute
                    zIndex = integer(10)
                }
            }
        }
    }
}