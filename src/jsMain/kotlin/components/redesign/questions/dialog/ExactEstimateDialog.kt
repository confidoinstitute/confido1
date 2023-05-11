package components.redesign.questions.dialog

import components.AppStateContext
import components.redesign.basic.Dialog
import components.redesign.basic.sansSerif
import components.redesign.forms.*
import components.showError
import csstype.*
import emotion.react.css
import hooks.useCoroutineLock
import react.FC
import react.Props
import react.dom.html.ButtonType
import react.dom.html.InputMode
import react.dom.html.ReactHTML.span
import react.useContext
import react.useState
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace

external interface ExactEstimateDialogProps : Props {
    var open: Boolean
    var question: Question
    var onClose: (() -> Unit)?
}

external interface BinaryExactEstimateDialogProps : ExactEstimateDialogProps {
    var myPredictionDist: BinaryDistribution?
}

val ExactEstimateDialog = FC<ExactEstimateDialogProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    if (props.question.answerSpace is BinarySpace) {
        val dist = appState.myPredictions[props.question.ref]?.dist as BinaryDistribution?
        BinaryExactEstimateDialog {
            +props
            myPredictionDist = dist
        }
    }
}


external interface ProbabilityInputProps : Props {
    var value: BinaryDistribution?
    var onChange: ((BinaryDistribution?) -> Unit)?
    var placeholder: String
    var required: Boolean
}

val ProbabilityInput = FC<ProbabilityInputProps> { props ->
    // TODO: Generalize as a TextInput with unit
    var value by useState(props.value?.yesProb?.times(100) ?: "")
    TextInput {
        this.value = value
        placeholder = props.placeholder
        required = props.required
        inputMode = InputMode.decimal
        onChange = { event ->
            value = event.target.value
            val new = try {
                event.target.value.toDouble()
            } catch (e: NumberFormatException) {
                Double.NaN
            }
            if (!new.isNaN() && new >= 0 && new <= 100) {
                props.onChange?.invoke(BinaryDistribution(new / 100))
            } else {
                props.onChange?.invoke(null)
            }
        }
    }
    span {
        css {
            position = Position.absolute
            // TODO: Positioning hack, might not work very well
            // TODO: text can go under this
            fontFamily = sansSerif
            fontSize = 17.px
            lineHeight = 21.px
            right = 0.px
            transform = translate(-30.px, 50.pct)
            userSelect = None.none
        }
        +"%"
    }
}

val BinaryExactEstimateDialog = FC<BinaryExactEstimateDialogProps> { props ->
    var estimate by useState(props.myPredictionDist)
    val submitLock = useCoroutineLock()

    fun estimate() {
        submitLock {
            estimate?.let { typedEstimate ->
                val dist: ProbabilityDistribution = typedEstimate
                Client.sendData("${props.question.urlPrefix}/predict", dist, onError = {
                    showError(it)
                }) {
                    props.onClose?.invoke()
                }
            }
        }
    }

    val disabled = estimate == null || submitLock.running

    Dialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = "Set exact ${props.question.predictionTerminology.term}"
        action = "Save"
        onAction = { estimate() }
        disabledAction = disabled
        Form {
            onSubmit = { estimate() }
            FormSection {
                FormField {
                    title = "Probability"
                    titleColor = Color("#00CC2E")
                    ProbabilityInput {
                        value = estimate
                        onChange = { estimate = it }
                    }
                }
                Button {
                    type = ButtonType.submit
                    this.disabled = disabled
                    +"Save"
                }
            }
        }
    }
}