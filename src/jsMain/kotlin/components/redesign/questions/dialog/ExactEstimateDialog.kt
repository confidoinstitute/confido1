package components.redesign.questions.dialog

import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.questions.predictions.NormalishDistSpec
import components.redesign.questions.predictions.NumericDistSpecSym
import components.redesign.questions.predictions.NumericPredGraph
import components.redesign.questions.predictions.SymmetrySwitch
import components.showError
import csstype.*
import dom.html.HTMLInputElement
import emotion.react.css
import hooks.useCoroutineLock
import kotlinx.datetime.*
import kotlinx.js.jso
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.Space
import tools.confido.utils.*
import kotlin.math.abs

external interface ExactEstimateDialogProps : Props {
    // HACK: Need 'val' here to allow subclasses to narrow down the type
    val space: Space
    var open: Boolean
    var question: Question
    var onClose: (() -> Unit)?
}

external interface BinaryExactEstimateDialogProps : ExactEstimateDialogProps {
    var myPredictionDist: BinaryDistribution?
    override var space: BinarySpace
}

external interface NumericExactEstimateDialogProps : ExactEstimateDialogProps {
    var myPredictionDist: ContinuousProbabilityDistribution?
    override var space: NumericSpace
}

val ExactEstimateDialog = FC<ExactEstimateDialogProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    props.question.answerSpace.let { answerSpace ->
        when (answerSpace) {
            BinarySpace -> {
                val dist = appState.myPredictions[props.question.ref]?.dist as BinaryDistribution?
                BinaryExactEstimateDialog {
                    +props
                    myPredictionDist = dist
                    this.space = BinarySpace
                }
            }

            is NumericSpace -> {
                val dist = appState.myPredictions[props.question.ref]?.dist
                NumericExactEstimateDialog {
                    +props
                    myPredictionDist = dist as? ContinuousProbabilityDistribution
                    this.space = answerSpace
                }
            }
            else-> {}
        }
    }
}


external interface ProbabilityInputProps : Props {
    var value: BinaryDistribution?
    var onChange: ((BinaryDistribution?) -> Unit)?
    var placeholder: String
    var required: Boolean
    var autoFocus: Boolean
}

val ProbabilityInput = FC<ProbabilityInputProps> { props ->
    // TODO: Generalize as a TextInput with unit
    var value by useState(props.value?.yesProb?.times(100)?.toFixed(2) ?: "")
    TextInput {
        this.value = value
        autoFocus = props.autoFocus
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

    useEffect(props.myPredictionDist) {
        estimate = props.myPredictionDist
    }

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
                    titleColor = MainPalette.center.color
                    ProbabilityInput {
                        value = estimate
                        onChange = { estimate = it }
                    }
                }
                Button {
                    type = ButtonType.submit
                    this.disabled = disabled
                    +"Save ${props.question.predictionTerminology.term}"
                }
            }
        }
    }
}

private val descriptionTextClass = emotion.css.ClassName {
    fontFamily = sansSerif
    fontSize = 17.px
    lineHeight = 21.px
}
private val numericTextBoxClass = emotion.css.ClassName {
    display = Display.inlineBlock
    fontWeight = integer(700)
    width = Globals.unset
    textAlign = TextAlign.center
}

val NumericExactEstimateDialog = FC<NumericExactEstimateDialogProps> { props ->
    val space = props.space
    val propDist = props.myPredictionDist
    var spec by useState(propDist?.let { NormalishDistSpec.fromDist(it) }
                                        ?: NumericDistSpecSym(space, null, null))
    val previewDist = spec.useDist()

    val submitLock = useCoroutineLock()

    fun parseInputValue(text: String) =
        if (text == "") null
        else if (space.representsDays) {
            try {
                val unixTime = LocalDate.parse(text).atTime(12, 0).toInstant(TimeZone.UTC).epochSeconds
                unixTime.toDouble()
            } catch (e: Exception) {
                null
            }
        } else {
            text.toDoubleOrNull()
        }
    fun formatForInput(v: Double?) =
        if (v == null) ""
        else if (space.representsDays) Instant.fromEpochSeconds(v.toLong()).toLocalDateTime(TimeZone.UTC).date.toString()
        else v.toFixed(space.reasonableDecimals)
    var lastEdited by useState(-1)
    data class ParamInput(
        val id: Int,
        val textInput: String,
        val specValue: Double?,
        private val setText: StateSetter<String>,
        val onChange: (Double)->Unit,
    ) {
        val numericInput get() = parseInputValue(textInput)
        val numericOrLast get() = numericInput ?: specValue
        fun setInput(v: String) {
            setText(v)
            lastEdited = id
            parseInputValue(v)?.let { onChange(it) }
        }
        fun setValue(v: Double?) = setText(formatForInput(v))
        val validNumber get() = (numericInput != null)
    }
    fun useParamInput(id: Int, cur: Double?, onChange: (Double)->Unit): ParamInput {
        val (text, setText) = useState(cur?.toString() ?: "")
        useEffect(cur) {
            if (lastEdited != id) setText(formatForInput(cur))
        }
        return ParamInput(id, text, cur, setText, onChange)
    }

    val centerParam = useParamInput(0, spec.center) { spec = spec.setCenter(it) }
    val lowerParam = useParamInput(1, spec.ci?.e1) { spec = spec.setCiBoundary(it, 0) }
    val upperParam = useParamInput(2, spec.ci?.e2) { spec = spec.setCiBoundary(it, 1) }


    useEffect(propDist?.identify()) {
        if (propDist != null && propDist != previewDist) {
            val newSpec = NormalishDistSpec.fromDist(propDist)
            spec = newSpec
            centerParam.setValue(newSpec.center)
            lowerParam.setValue(newSpec.ci?.e1)
            upperParam.setValue(newSpec.ci?.e2)
        }
    }

    // TODO: Some kind of textbox highlights when set, along with errors
    var lowerLimitComment by useState<String?>(null)
    var upperLimitComment by useState<String?>(null)

    val centerError =
        if (centerParam.textInput == "") ""
        else if (centerParam.numericInput == null) "invalid number"
        else if (!space.range.contains(centerParam.numericInput!!)) {
            "The center value has to be within the answer range (${space.min} to ${space.max})."
        } else {
            null
        }
    val ciError =
            centerParam.numericInput?.let { center ->
                lowerParam.numericInput?.let { lowerBound ->
                    if (lowerBound >= center) {
                        "The start of your confidence interval has to be smaller than the center value"
                    } else {
                        upperParam.numericInput?.let { upperBound ->
                            if (upperBound <= center) {
                                "The end of your confidence interval has to be bigger than the center value"
                            } else {
                                null
                            }
                        }
                    }
                }
            }

    val lowerClampCommentText = "The start of your confidence interval has been clamped to the answer range."
    val higherClampCommentText = "The end of your confidence interval has been clamped to the answer range."




    // XXX this should ideally be handled by a parent PredictionInput instead of
    // wild-west sending it directly from here
    fun send() {
        submitLock {
            previewDist?.let { dist ->
                Client.sendData("${props.question.urlPrefix}/predict", dist as ProbabilityDistribution, onError = {
                    showError(it)
                }) {
                    props.onClose?.invoke()
                }
            }
        }
    }

    val inputType = if (space.representsDays) { InputType.date } else { InputType.text }
    val inputMode = if (space.representsDays) { null } else { InputMode.numeric }
    val disabled = (!spec.complete) || ciError != null || centerError != null || submitLock.running

    val confidenceColor = Color("#00C2FF")
    val rangeColor = Color("#0066FF")

    val inputParams = jso<InputHTMLAttributes<HTMLInputElement>> {
        type = inputType
        this.inputMode = inputMode
        min = space.formatValue(space.min)
        max = space.formatValue(space.max)
    }

    Dialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = "Set exact ${props.question.predictionTerminology.term}"
        action = "Save"
        onAction = { send() }
        disabledAction = disabled
        Form {
            onSubmit = { send() }
            FormSection {
                NumericPredGraph {
                    key = "exactPredictionGraph"
                    this.space = space
                    dist = previewDist
                    isGroup = false
                }
                FormField {
                    title = "Center"
                    titleColor = MainPalette.center.color
                    error = centerError
                    div {
                        className = descriptionTextClass
                        +"You think that the most probable answer is "
                        TextInput {
                            css(override = numericTextBoxClass) {
                                color = MainPalette.center.color
                            }
                            size = maxOf(1, centerParam.textInput.length - 4)
                            value = centerParam.textInput
                            onChange = { e -> centerParam.setInput(e.target.value) }
                            +inputParams
                        }
                        +" ${space.unit}."
                    }
                }
                FormField {
                    title = "Uncertainty"
                    titleColor = Color("#0066FF")
                    this.comment = "${lowerLimitComment ?: ""} ${upperLimitComment ?: ""}"
                    this.error = ciError
                    Stack {
                        div {
                            className = descriptionTextClass
                            +"You are "
                            ReactHTML.b {
                                css { this.color = confidenceColor }
                                +"${formatPercent(NormalishDistSpec.ciConfidence, space = false)} "
                            }
                            +"confident that the answer is between "
                            TextInput {
                                css(override = numericTextBoxClass) {
                                    color = rangeColor
                                }
                                size = maxOf(1, lowerParam.textInput.length - 4)
                                value = lowerParam.textInput
                                +inputParams
                                onChange = { e -> lowerParam.setInput(e.target.value) }
                            }
                            +" ${space.unit} and "
                            TextInput {
                                css(override = numericTextBoxClass) {
                                    color = rangeColor
                                }
                                size = maxOf(1, upperParam.textInput.length - 4)
                                value = upperParam.textInput
                                +inputParams
                                onChange = { e -> upperParam.setInput(e.target.value) }
                            }
                            +" ${space.unit}."
                        }
                    }
                }
                FormField {
                    title = "Symmetry"
                    div {
                        label {
                            Stack {
                                direction = FlexDirection.row
                                css {
                                    alignItems = AlignItems.center
                                    fontFamily = sansSerif
                                    gap = 10.px
                                }
                                span {
                                    css {
                                        if (!spec.asymmetric) fontWeight = FontWeight.bold
                                    }
                                    +"Symmetric"
                                }
                                SymmetrySwitch {
                                    checked = spec.asymmetric
                                    wrapLabel = false
                                    onChange = { e ->
                                        spec = spec.setAsymmetric(e.target.checked)
                                    }
                                }
                                span {

                                    css {
                                        if (spec.asymmetric) fontWeight = FontWeight.bold
                                    }
                                    +"Asymmetric"
                                }
                            }
                        }
                    }
                }
                Button {
                    type = ButtonType.submit
                    this.disabled = disabled
                    +"Save ${props.question.predictionTerminology.term}"
                }
            }
        }
    }
}
