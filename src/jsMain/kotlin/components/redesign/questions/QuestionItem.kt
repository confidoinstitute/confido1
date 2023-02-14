package components.redesign.questions

import components.AppStateContext
import csstype.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.PredictionTerminology
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.utils.pluralize
import tools.confido.utils.unixNow
import web.timers.clearInterval
import web.timers.setInterval

private enum class QuestionState {
    OPEN,
    CLOSED,
    RESOLVED,
    ANNULLED,
}

external interface QuestionItemProps : Props {
    var question: Question
    var onClick: (() -> Unit)?
}

private data class QuestionItemColors(
    val questionColor: Color,
    val secondaryColor: Color,
    val mutedColor: Color,
)

fun agoPrefix(timestamp: Int): String {
    return when (val diff = unixNow() - timestamp) {
        in 0..120 -> "$diff ${pluralize("second", diff)}"
        in 120..7200 -> "${diff / 60} ${pluralize("minute", diff / 60)}"
        in 7200..172800 -> "${diff / 3600} ${pluralize("hour", diff / 3600)}"
        else -> "${diff / 86400} ${pluralize("day", diff / 86400)}"
    }
}

val QuestionItem = FC<QuestionItemProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val prediction = appState.myPredictions[props.question.ref]

    var questionState = QuestionState.OPEN
    if (!props.question.open)
        questionState = QuestionState.CLOSED
    if (props.question.resolved && props.question.resolutionVisible)
        questionState = QuestionState.RESOLVED

    // TODO: Backend support for Annulled state.

    val colors = when (questionState) {
        QuestionState.OPEN -> QuestionItemColors(
            questionColor = Color("#000000"),
            secondaryColor = Color("#5433B4"),
            mutedColor = Color("rgba(84, 51, 180, 0.5)"),
        )

        QuestionState.CLOSED -> QuestionItemColors(
            questionColor = Color("#000000"),
            secondaryColor = Color("#0B65B8"),
            mutedColor = Color("rgba(11, 101, 184, 0.5)"),
        )

        QuestionState.RESOLVED -> QuestionItemColors(
            questionColor = Color("#000000"),
            secondaryColor = Color("#0A9653"),
            mutedColor = Color("rgba(10, 150, 83, 0.5)"),
        )

        QuestionState.ANNULLED -> QuestionItemColors(
            questionColor = Color("#000000"),
            secondaryColor = Color("#8B8B8B"),
            mutedColor = Color("rgba(139, 139, 139, 0.5)"),
        )
    }

    val predictionTerm = when (props.question.predictionTerminology) {
        PredictionTerminology.PREDICTION -> "prediction"
        PredictionTerminology.ANSWER -> "answer"
        PredictionTerminology.ESTIMATE -> "estimate"
    }

    var predictionAgoPrefix by useState<String?>(null)

    useEffect(prediction?.ts) {
        if (prediction == null)
            return@useEffect

        fun setText() {
            predictionAgoPrefix = agoPrefix(prediction.ts)
        }
        setText()
        val interval = setInterval(::setText, 5000)

        cleanup {
            clearInterval(interval)
        }
    }

    div {
        css {
            display = Display.flex;
            flexDirection = FlexDirection.column
            alignItems = AlignItems.flexStart
            padding = Padding(9.px, 16.px)

            background = Color("#FFFFFF")
            borderRadius = 10.px
        }

        onClick = { props.onClick?.invoke() }

        // Question Status Frame
        div {
            css {
                display = Display.flex;
                flexDirection = FlexDirection.row
                alignItems = AlignItems.flexStart
                padding = Padding(0.px, 0.px, 2.px)
            }
            span {
                css {
                    textTransform = TextTransform.uppercase
                    fontFamily = FontFamily.sansSerif
                    fontStyle = FontStyle.normal
                    fontWeight = FontWeight.bold
                    fontSize = 11.px
                    lineHeight = 13.px
                    color = colors.mutedColor
                }

                val stateLabel = when (questionState) {
                    QuestionState.OPEN -> "OPEN"
                    QuestionState.CLOSED -> "CLOSED"
                    QuestionState.RESOLVED -> "RESOLVED"
                    QuestionState.ANNULLED -> "ANNULLED"
                }
                +stateLabel
                // TODO: append ", closing in X hours" | ", closing in X days" if scheduled to close
            }
        }

        // Question Frame
        div {
            css {
                display = Display.flex;
                flexDirection = FlexDirection.row
                alignItems = AlignItems.flexStart
                padding = Padding(0.px, 0.px, 4.px)
            }
            span {
                css {
                    fontFamily = FontFamily.serif
                    fontWeight = 500.unsafeCast<FontWeight>()
                    fontSize = 24.px
                    lineHeight = 29.px
                    color = colors.questionColor
                }
                +props.question.name
            }
        }

        // "Footer" Frame
        div {
            css {
                display = Display.flex;
                flexDirection = FlexDirection.row
                alignItems = AlignItems.flexEnd
                justifyContent = JustifyContent.spaceBetween
                alignSelf = AlignSelf.stretch
                padding = 0.px
                gap = 10.px
                flexWrap = FlexWrap.wrap
            }

            // Answers Frame
            div {
                css {
                    display = Display.flex;
                    flexDirection = FlexDirection.row
                    alignItems = AlignItems.baseline
                    flex = None.none
                    padding = 0.px
                    gap = 10.px
                }
                span {
                    css {
                        fontFamily = FontFamily.sansSerif
                        fontStyle = FontStyle.normal
                        fontWeight = FontWeight.bold
                        fontSize = 14.px
                        lineHeight = 17.px
                        color = colors.secondaryColor
                    }

                    +"${props.question.numPredictors} ${pluralize(predictionTerm, props.question.numPredictors)}"
                }
            }

            // Estimate Frame
            div {
                css {
                    display = Display.flex;
                    flexDirection = FlexDirection.column
                    alignItems = AlignItems.flexStart

                    flexGrow = number(1.0)
                }

                // Value Frame
                div {
                    css {
                        fontFamily = FontFamily.sansSerif
                        fontStyle = FontStyle.normal
                        fontWeight = FontWeight.bold
                        fontSize = 28.px
                        lineHeight = 33.px
                        color = colors.secondaryColor
                        textAlign = TextAlign.right
                        width = 100.pct
                    }

                    fun renderDistribution(distribution: ProbabilityDistribution) {
                        val space = distribution.space
                        when (distribution) {
                            is BinaryDistribution -> {
                                +"${distribution.yesProb * 100}%"
                            }

                            is ContinuousProbabilityDistribution -> {
                                val interval = distribution.confidenceInterval(0.8)
                                +"${space.formatValue(interval.start, showUnit = false)} to ${space.formatValue(interval.endInclusive)}"
                            }
                        }
                    }

                    // If open:
                    // -> do NOT show group prediction before making a prediction
                    // If closed:
                    // -> show group prediction
                    // If resolved:
                    // -> show resolved value
                    // If annulled:
                    // -> ?
                    // If prediction is made
                    // -> show group prediction or answer if group prediction not available

                    if (questionState == QuestionState.RESOLVED) {
                        props.question.resolution?.format()?.let {
                            +it
                        } ?: run {
                            console.error("Missing resolution for resolved question")
                            br {}
                        }
                    } else {
                        if (prediction != null) {
                            renderDistribution(prediction.dist)
                            // TODO: Group estimate if available
                        } else {
                            br {}
                        }
                    }
                }

                // Label Frame
                div {
                    css {
                        fontFamily = FontFamily.sansSerif
                        fontStyle = FontStyle.normal
                        fontWeight = FontWeight.bold
                        fontSize = 11.px
                        lineHeight = 13.px
                        color = colors.mutedColor
                        textAlign = TextAlign.right
                        alignSelf = AlignSelf.baseline
                        width = 100.pct
                        padding = Padding(0.px, 0.px, 2.px)
                    }
                    if (questionState == QuestionState.RESOLVED) {
                        +"correct answer"
                    } else {
                        if (prediction != null) {
                            +"your $predictionTerm from $predictionAgoPrefix ago"
                            // TODO: "group estimate" if available
                        } else if (questionState == QuestionState.OPEN) {
                            if (props.question.numPredictions == 0) {
                                +"be the first to answer!"
                            } else {
                                if (props.question.groupPredVisible) {
                                    +"answer to see the group $predictionTerm"
                                } else {
                                    +"add your $predictionTerm"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
