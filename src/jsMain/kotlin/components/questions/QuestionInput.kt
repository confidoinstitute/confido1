package components.questions

import components.*
import csstype.*
import emotion.react.css
import kotlinx.js.get
import mui.material.Slider
import mui.material.Typography
import mui.material.styles.Theme
import mui.material.styles.useTheme
import mui.system.Box
import mui.system.Breakpoint
import mui.system.sx
import react.*
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.em
import space.kscience.dataforge.values.Value
import space.kscience.dataforge.values.asValue
import tools.confido.distributions.*
import tools.confido.spaces.*
import tools.confido.utils.formatPercent
import utils.*
import kotlin.js.Date

external interface QuestionInputProps<S : Space, D: ProbabilityDistribution> : Props {
    var id: String
    var enabled: Boolean
    var space: S
    var prediction: D?
    var onPredict: ((D) -> Unit)?
    var onChange: (() -> Unit)?
}

val NumericQuestionInput = FC<QuestionInputProps<NumericSpace, ContinuousProbabilityDistribution>> { props ->
    val space = props.space
    val prediction = props.prediction as? TruncatedNormalDistribution ?: TruncatedNormalDistribution(props.space, (space.min + space.max) / 2.0, (space.max - space.min) / 4)
    var madePrediction by useState(props.prediction != null)
    var madeUncertainty by useState(madePrediction)


    var mean by useState(prediction.pseudoMean)
    var stdev by useState(prediction.pseudoStdev)
    val dist = TruncatedNormalDistribution(space, mean, stdev)

    val step = if (space.representsDays) 86400 else 0.1

    val confidences = useMemo(props.enabled) {
        if (props.enabled) listOf(
            ConfidenceColor(0.5, "#88d4e7".asValue()),
            ConfidenceColor(0.7, "#55a3b5".asValue()),
            ConfidenceColor(0.9, "#675491".asValue()),
        ) else listOf(
            ConfidenceColor(0.5, "#bfbfbf".asValue()),
            ConfidenceColor(0.7, "#acacac".asValue()),
            ConfidenceColor(0.9, "#a0a0a0".asValue()),
        )
    }

    fun sendPrediction() {
        if (madeUncertainty)
            props.onPredict?.invoke(dist)
    }

    fun formatDate(value: Number): String = Date(value.toDouble() * 1000.0).toISOString().substring(0, 10)
    val formatUncertainty = {_: Number -> if (!madeUncertainty) "Specify your uncertainty" else "Uncertainty"}

    fun addUnit(value: Any) = if (space.unit.isNotEmpty()) "$value ${space.unit}" else value.toString()

    Fragment {
        Box {
            sx {
                padding = Padding(horizontal = 1.2.rem, vertical = 0.px)
            }
            SimpleContDistPlot {
                this.dist = dist
                this.confidences = confidences
                this.outsideColor = if (props.enabled) Value.of("#3a2b63") else Value.of("#9c9c9c")
                this.visible = madePrediction && madeUncertainty
            }
            Typography {
                css {
                    color = Color("#999")
                    fontWeight = FontWeight.bold
                }
                +"Your estimate:"
            }
            MarkedSlider {
                ariaLabel = "Mean Value"

                disabled = !props.enabled
                value = mean
                min = space.min
                max = space.max
                this.step = step
                this.madePrediction = madePrediction
                this.unit = space.unit
                preciseInputForm = if (space.representsDays) PreciseInputDate else PreciseInputNumber

                valueLabelDisplay = if (madePrediction || !props.enabled) "auto" else "on"
                if (space.representsDays) {
                    this.widthToMarks = { width -> dateMarkSpacing(width, space.min, space.max) }
                }
                this.valueLabelFormat = { space.formatValue(it.toDouble()) }

                onFocus = { madePrediction = true }
                onChange = { _, value, _ -> mean = value; if(madeUncertainty) props.onChange?.invoke() }
                onChangeCommitted = { _, _ -> sendPrediction() }
            }
            Typography {
                css {
                    marginTop = 8.px
                    color = Color("#999")
                    fontWeight = FontWeight.bold
                }
                +"Your uncertainty:"
            }
            Slider {
                ariaLabel = "Uncertainty"

                disabled = !props.enabled || !madePrediction
                value = stdev
                min = 0.1
                max = (space.max - space.min) / 2
                this.step = 0.1

                valueLabelDisplay = if (madeUncertainty || !madePrediction) "off" else "on"
                //if (!madeUncertainty)
                    valueLabelFormat = formatUncertainty
                track = if (madeUncertainty) "normal" else false.asDynamic()
                onFocus = { madePrediction = true; madeUncertainty = true }
                onChange = { _, value, _ -> stdev = value; props.onChange?.invoke() }
                onChangeCommitted = { _, _ -> sendPrediction() }
            }
        }
        if (madePrediction && madeUncertainty) {
            confidences.map { confidence ->
                Typography {
                    val confidenceInterval = dist.confidenceInterval(confidence.p)
                    +"You are "
                    ReactHTML.strong {
                        css {
                            color = Color(confidence.color.toString())
                        }
                        +"${confidence.p * 100}%"
                    }
                    +" confident that the value lies between ${space.formatValue(confidenceInterval.start)} and ${space.formatValue(confidenceInterval.endInclusive)}"
                }
            }
        }
    }
}

val BinaryQuestionInput = FC<QuestionInputProps<BinarySpace, BinaryDistribution>> { props ->
    var estimate by useState(props.prediction?.yesProb ?: 0.5)
    var madePrediction by useState(props.prediction != null)



    fun sendPrediction() {
        props.onPredict?.invoke(BinaryDistribution(estimate))
    }

    val breakpoints = useTheme<Theme>().breakpoints.values
    fun getBp(b: Breakpoint) = breakpoints[b]?.toDouble()!!
    fun getMarks(width: Double): List<Number> = when(width) {
        in 0.0 .. getBp(Breakpoint.xs) -> listOf(0, 1.0)
        in getBp(Breakpoint.xs) .. getBp(Breakpoint.sm) -> listOf(0, 0.5, 1.0)
        in getBp(Breakpoint.sm) .. getBp(Breakpoint.md) -> listOf(0, 0.25, 0.5, 0.75, 1.0)
        else -> (0..10).map { it / 10.0 }
    }

    Fragment {
        Box {
            sx {
                padding = Padding(horizontal = 1.2.rem, vertical = 0.px)
            }
            MarkedSlider {
                ariaLabel = "Certainty"

                disabled = !props.enabled
                value = estimate
                min = 0
                max = 1
                step = 0.01
                unit = "%"
                preciseInputForm = PreciseInputPercent

                this.widthToMarks = ::getMarks
                valueLabelDisplay = if (madePrediction || !props.enabled) "auto" else "on"
                valueLabelFormat = { formatPercent(it) }
                this.madePrediction = madePrediction

                onFocus = { madePrediction = true }
                onChange = { _, value, _ -> estimate = value; props.onChange?.invoke() }
                onChangeCommitted = { _, _ -> sendPrediction() }
            }

            div {
                css {
                    width = 100.pct
                    display = Display.flex
                    flexDirection =  FlexDirection.row
                    marginTop = 5.px
                }
                div {
                    css {
                        width = 50.pct
                        maxWidth = 50.pct
                        borderTop = Border(3.px, LineStyle.solid, ConfidoColors.RED)
                        color = ConfidoColors.RED
                        textAlign = TextAlign.right
                        paddingRight = 1.em
                        display = Display.flex
                        flexDirection = FlexDirection.row
                        justifyContent = JustifyContent.spaceBetween
                    }
                    Typography {  + "no" }
                    Typography {  + "← likely no" }
                }
                div {
                    css {
                        width = 50.pct
                        maxWidth = 50.pct
                        borderTop = Border(3.px, LineStyle.solid, ConfidoColors.GREEN)
                        color = ConfidoColors.GREEN
                        textAlign = TextAlign.left
                        paddingLeft = 1.em
                        display = Display.flex
                        flexDirection = FlexDirection.row
                        justifyContent = JustifyContent.spaceBetween
                    }
                    div { + "likely yes →"}
                    div { +"yes" }
                }
            }
        }

        Typography {
            fun certaintyExplanation() {
                Explanation {
                    title = "Absolute certainty"
                    Typography {
                        +"To say something with absolute certainty is an extremely strong claim. In most cases during forecasting, there is still even a minimal chance that the true answer is different from your expectations."
                    }
                }
            }
            if (madePrediction) {
                when (estimate) {
                    0.0 -> {
                        +"You think there is "
                        ReactHTML.strong {
                            +"absolutely no chance"
                        }
                        +". (Are you sure?)"
                        certaintyExplanation()
                    }
                    1.0 -> {
                        +"You think this is an "
                        ReactHTML.strong {
                            +"absolute certainty"
                        }
                        +". (Are you sure?)"
                        certaintyExplanation()
                    }
                    else -> {
                        +"You think there is a "
                        ReactHTML.strong {
                            +formatPercent(estimate, false)
                        }
                        +" chance."
                    }
                }
            } else {
                em {
                    +"No prediction made yet."
                }
            }
        }
    }
}
