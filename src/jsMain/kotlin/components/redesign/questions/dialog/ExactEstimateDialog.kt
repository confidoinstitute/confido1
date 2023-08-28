package components.redesign.questions.dialog

import components.AppStateContext
import components.redesign.basic.Dialog
import components.redesign.basic.MainPalette
import components.redesign.basic.css
import components.redesign.basic.sansSerif
import components.redesign.forms.*
import components.redesign.questions.predictions.NumericPredGraph
import components.showError
import csstype.*
import emotion.react.css
import hooks.useCoroutineLock
import kotlinx.datetime.*
import react.*
import react.dom.html.ButtonType
import react.dom.html.InputMode
import react.dom.html.InputType
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.distributions.BinaryDistribution
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
    var myPredictionDist: TruncatedNormalDistribution?
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
                when (dist) {
                    is TruncatedNormalDistribution ->
                    SymmetricNumericExactEstimateDialog {
                        +props
                        myPredictionDist = dist
                        this.space = answerSpace
                    }
                }
            }
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

val SymmetricNumericExactEstimateDialog = FC<NumericExactEstimateDialogProps> { props ->

    fun findDistribution(space: NumericSpace, center: Double, ciWidth: Double): TruncatedNormalDistribution {
        val pseudoStdev = binarySearch(0.0..4*ciWidth, ciWidth, 30) {
                TruncatedNormalDistribution(space, center, it).confidenceInterval(0.8).size
            }.mid
        return TruncatedNormalDistribution(space, center, pseudoStdev)
    }
    val submitLock = useCoroutineLock()
    val space = props.space
    val confidence = 0.8
    // TODO: This limit is shared with NumericPredInput, deduplicate
    // For CIWidth -> 0.8 this converges to uniform distribution
    //     CIWidth > 0.8 there is no solution (the distribution would need to be convex) and distribution search
    //     diverges, returns astronomically large stdev and creates weird artifacts
    val maxCIWidth = 0.798 * space.size

    var pseudoMean by useState<Double?>(null)
    var ciRadius by useState<Double?>(null)

    val ciWidth = pseudoMean?.let { center ->
        ciRadius?.let { radius ->
            minOf(radius, center - space.min) + minOf(radius, space.max - center)
        }
    }

    val lowerBound = ciRadius?.let { pseudoMean?.minus(it) }
    val upperBound = ciRadius?.let { pseudoMean?.plus(it) }

    var centerText by useState("")
    var lowerBoundText by useState("")
    var upperBoundText by useState("")

    // TODO: Input debouncing

    // TODO: Some kind of textbox highlights when set, along with errors
    var lowerLimitComment by useState<String?>(null)
    var upperLimitComment by useState<String?>(null)

    val centerError = pseudoMean?.let {
        if (!space.range.contains(it)) {
            "The center value has to be within the answer range (${space.min} to ${space.max})."
        } else {
            null
        }
    }
    val ciError = ciWidth?.let {
        if (ciWidth >= maxCIWidth) {
            "The confidence interval is too wide. Please try a narrower range."
        } else {
            pseudoMean?.let { center ->
                lowerBound?.let { lowerBound ->
                    if (lowerBound >= center) {
                        "The start of your confidence interval has to be smaller than the center value"
                    } else {
                        upperBound?.let { upperBound ->
                            if (upperBound <= center) {
                                "The end of your confidence interval has to be bigger than the center value"
                            } else {
                                null
                            }
                        }
                    }
                }
            }
        }
    }

    val lowerClampCommentText = "The start of your confidence interval has been clamped to the answer range."
    val higherClampCommentText = "The end of your confidence interval has been clamped to the answer range."

    useEffect(props.myPredictionDist) {
        props.myPredictionDist?.let { dist ->
            pseudoMean = dist.pseudoMean
            val radius = maxOf(
                abs(dist.confidenceInterval(confidence).start - dist.pseudoMean),
                abs(dist.confidenceInterval(confidence).endInclusive - dist.pseudoMean)
            )
            ciRadius = radius
            val confidenceInterval = dist.confidenceInterval(confidence)
            centerText = space.formatValue(dist.pseudoMean, showUnit = false)
            lowerBoundText = space.formatValue(confidenceInterval.start, showUnit = false)
            upperBoundText = space.formatValue(confidenceInterval.endInclusive, showUnit = false)
            if (dist.pseudoMean + radius > space.max) {
                upperLimitComment = higherClampCommentText
            }
            if (dist.pseudoMean - radius < space.min) {
                lowerLimitComment = lowerClampCommentText
            }
        } ?: run {
            pseudoMean = null
            ciRadius = null
            centerText = ""
            lowerBoundText = ""
            upperBoundText = ""
            lowerLimitComment = null
            upperLimitComment = null
        }
    }

    fun readInputValue(text: String): Double? {
        return if (space.representsDays) {
            try {
                val unixTime = LocalDate.parse(text).atTime(12, 0).toInstant(TimeZone.UTC).epochSeconds
                unixTime.toDouble()
            } catch (e: Exception) {
                null
            }
        } else {
            text.toDoubleOrNull()
        }
    }

    fun setCenterText(newText: String) {
        centerText = newText
        // The center has shifted, update both confidence interval start and end, keeping the same radius.
        readInputValue(newText)?.let { newCenter ->
            pseudoMean = newCenter

            ciRadius?.let { ciRadius ->
                val newUpperBound = if (newCenter + ciRadius > space.max) {
                    upperLimitComment = higherClampCommentText
                    space.max
                } else {
                    upperLimitComment = null
                    newCenter + ciRadius
                }
                upperBoundText = space.formatValue(newUpperBound, showUnit = false)
            }

            ciRadius?.let { ciRadius ->
                val newLowerBound = if (newCenter - ciRadius < space.min) {
                    lowerLimitComment = lowerClampCommentText
                    space.min
                } else {
                    lowerLimitComment = null
                    newCenter - ciRadius
                }
                lowerBoundText = space.formatValue(newLowerBound, showUnit = false)
            }
        }
    }

    fun setLowerBoundText(newText: String) {
        lowerBoundText = newText
        // Update the upper bound text as it is symmetric.
        pseudoMean?.let { center ->
            readInputValue(newText)?.let {
                val newRadius = center - it
                ciRadius = newRadius
                val newUpperBound = if (center + newRadius > space.max) {
                    upperLimitComment = higherClampCommentText
                    space.max
                } else {
                    upperLimitComment = null
                    center + newRadius
                }
                upperBoundText = space.formatValue(newUpperBound, showUnit = false)
            }
        }
    }

    fun setUpperBoundText(newText: String) {
        upperBoundText = newText
        // Update the lower bound text as it is symmetric.
        pseudoMean?.let { center ->
            readInputValue(newText)?.let {
                val newRadius = it - center
                ciRadius = newRadius
                val newLowerBound = if (center - newRadius < space.min) {
                    lowerLimitComment = lowerClampCommentText
                    space.min
                } else {
                    lowerLimitComment = null
                    center - newRadius
                }
                lowerBoundText = space.formatValue(newLowerBound, showUnit = false)
            }
        }
    }

    val estimate = useMemo(pseudoMean, ciRadius, space) {
        pseudoMean?.let { center ->
            ciWidth?.let { ciWidth ->
                findDistribution(space, center, ciWidth.coerceIn(0.0..maxCIWidth))
            }
        }
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

    val inputType = if (space.representsDays) { InputType.date } else { InputType.text }
    val inputMode = if (space.representsDays) { null } else { InputMode.numeric }
    val disabled = estimate == null || ciError != null || centerError != null || submitLock.running

    val confidenceColor = Color("#00C2FF")
    val rangeColor = Color("#0066FF")

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
                NumericPredGraph {
                    key = "exactPredictionGraph"
                    this.space = space
                    dist = estimate
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
                            size = maxOf(1, centerText.length - 4)
                            value = centerText
                            type = inputType
                            this.inputMode = inputMode
                            min = space.formatValue(space.min)
                            max = space.formatValue(space.max)
                            onChange = { e -> setCenterText(e.target.value) }
                        }
                        +" ${space.unit}."
                    }
                }
                FormField {
                    title = "Uncertainty"
                    titleColor = Color("#0066FF")
                    this.comment = "${lowerLimitComment ?: ""} ${upperLimitComment ?: ""}"
                    this.error = ciError
                    div {
                        className = descriptionTextClass
                        +"You are "
                        ReactHTML.b {
                            css { this.color = confidenceColor }
                            +"${formatPercent(confidence, space = false)} "
                        }
                        +"confident that the answer is between "
                        TextInput {
                            css(override = numericTextBoxClass) {
                                color = rangeColor
                            }
                            size = maxOf(1, lowerBoundText.length - 4)
                            value = lowerBoundText
                            type = inputType
                            this.inputMode = inputMode
                            onChange = { e -> setLowerBoundText(e.target.value) }
                        }
                        +" ${space.unit} and "
                        TextInput {
                            css(override = numericTextBoxClass) {
                                color = rangeColor
                            }
                            size = maxOf(1, upperBoundText.length - 4)
                            value = upperBoundText
                            type = inputType
                            this.inputMode = inputMode
                            onChange = { e -> setUpperBoundText(e.target.value) }
                        }
                        +" ${space.unit}."
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
