package extensions

import components.redesign.basic.Stack
import components.redesign.basic.sansSerif
import components.redesign.forms.*
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.redesign.questions.predictions.PredictionInputProps
import components.redesign.questions.predictions.SIDE_PAD
import csstype.*
import dom.html.HTML.div
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.extensions.get
import tools.confido.extensions.with
import tools.confido.question.PredictionTerminology
import tools.confido.question.Question
import tools.confido.spaces.NumericSpace

private val PointEstimateInput = FC<PredictionInputProps>("PointEstimateInput") { props ->
    val terminology = props.question?.predictionTerminology ?:   PredictionTerminology.PREDICTION
    val space = props.space as NumericSpace
    val propDist = props.dist as? PointEstimateContinuousDistribution?
    var value by useState(propDist?.value)
    var err by useState<InputError>()

    useEffect(props.dist?.identify()) {
        if (propDist != null && propDist.value != value) {
            value = propDist.value
            err = null
        }
    }

    Stack {
        css {
            overflowX = Overflow.hidden
            gap = 10.px
            padding = Padding(10.px, 0.px)
        }

        PointEstimateSlider {
            this.space = space
            this.value = value
            this.disabled = props.disabled
            this.question = props.question
            this.onChange = { newValue, isCommit ->
                value = newValue
                val dist = PointEstimateContinuousDistribution(space, newValue)
                props.onChange?.invoke(dist, isCommit)
            }
        }

        Stack {
            css {
                padding = Padding(0.px, SIDE_PAD.px)
                alignItems = AlignItems.baseline
            }
            direction = FlexDirection.row
            div {
                +"Or enter ${terminology.term} manually: "
                css {
                    whiteSpace = WhiteSpace.nowrap
                    fontFamily = sansSerif
                    fontWeight = integer(600)
                    color = Color("#999")
                }
            }
            NumericInput {
                css {
                    flexGrow = number(1.0)
                    flexShrink = number(1.0)
                    if (err != null) {
                        backgroundColor = Color("rgba(255,0,0,0.4)")
                    }
                }
                this.value = value
                min = space.min
                max = space.max
                step = 0.1
                disabled = props.disabled
                placeholder = "Enter value"
                onChange = { newValue, error ->
                    console.log("Numeric change $newValue err=$error")
                    err = error
                    if (error == null) {
                        newValue?.let { v ->
                            value = newValue
                            val dist = PointEstimateContinuousDistribution(space, v)
                            props.onChange?.invoke(dist, true)
                        }
                    }
                }
            }
        }
        if (err !=null)
            div {
                css {
                    textAlign = TextAlign.right
                    color = NamedColor.red
                    fontSize = 12.px
                    fontWeight = integer(600)
                    marginRight = SIDE_PAD.px
                }
                +err.toString()
            }
    }
}

object PointEstimateCE : ClientExtension, PointEstimateExtension() {
    override fun renderPredictionInput(props: PredictionInputProps): ReactNode? {
        val question = props.question ?: return null
        if (!question.extensionData[PointEstimateKey]) return null
        if (props.space !is NumericSpace) return null
        return PointEstimateInput.create { +props }
    }

    @Suppress("UNCHECKED_CAST")
    override fun editQuestionDialogExtra(props: EditQuestionDialogProps, cb: ChildrenBuilder) {
        var pointEstimate by useContextState(
            ExtensionContextPlace.EDIT_QUESTION_DIALOG,
            "point_estimate",
            props.entity?.extensionData?.get(PointEstimateKey) ?: false
        )

        cb.apply {
            FormSection {
                title = "Point Estimate"
                FormField {
                    title = "Allow only point estimates"
                    comment = "If enabled, users can only input single values instead of probability distributions."
                    FormSwitch {
                        checked = pointEstimate
                        onChange = { e -> pointEstimate = e.target.checked }
                    }
                }
            }
        }
    }

    override fun assembleQuestion(q: Question, states: Map<String, dynamic>): Question {
        val pointEstimate = states["point_estimate"]
        return q.copy(extensionData = q.extensionData.with(PointEstimateKey, pointEstimate))
    }

    override fun  hideDefaultPredictionDesc(dist: ProbabilityDistribution?, question: Question?, b: Boolean) =
        (dist is PointEstimateContinuousDistribution)
}
