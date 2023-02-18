package components.redesign.questions

import components.AppStateContext
import components.redesign.basic.QuestionPalette
import csstype.*
import emotion.react.css
import hooks.useTimeAgo
import react.*
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Prediction
import tools.confido.question.PredictionTerminology
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.utils.pluralize
import tools.confido.utils.toFixed

private enum class QuestionState {
    OPEN,
    CLOSED,
    RESOLVED,
    ANNULLED,
}

external interface QuestionItemProps : Props {
    var question: Question
    var groupPred: Prediction?
    var onClick: (() -> Unit)?
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
    val palette = when (questionState) {
        QuestionState.OPEN -> QuestionPalette.open
        QuestionState.CLOSED -> QuestionPalette.closed
        QuestionState.RESOLVED -> QuestionPalette.resolved
        QuestionState.ANNULLED -> QuestionPalette.annulled
    }

    val predictionTerm = when (props.question.predictionTerminology) {
        PredictionTerminology.PREDICTION -> "prediction"
        PredictionTerminology.ANSWER -> "answer"
        PredictionTerminology.ESTIMATE -> "estimate"
    }

    val predictionAgoText = useTimeAgo(prediction?.ts)

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
                    color = palette.color
                    opacity = number(0.5)
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
                    fontWeight = integer(500)
                    fontSize = 24.px
                    lineHeight = 29.px
                    color = Color("#000000")
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
                        color = palette.color
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
                        color = palette.color
                        textAlign = TextAlign.right
                        width = 100.pct
                    }

                    fun renderDistribution(distribution: ProbabilityDistribution) {
                        val space = distribution.space
                        when (distribution) {
                            is BinaryDistribution -> {
                                +"${(distribution.yesProb * 100).toFixed(0)}%"
                            }

                            is ContinuousProbabilityDistribution -> {
                                val interval = distribution.confidenceInterval(0.8)
                                +"${
                                    space.formatValue(
                                        interval.start,
                                        showUnit = false
                                    )
                                } to ${space.formatValue(interval.endInclusive)}"
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
                            // We show the group prediction if available,
                            // otherwise we show the latest prediction made by us.
                            props.groupPred?.let {
                                renderDistribution(it.dist)
                            } ?: run {
                                renderDistribution(prediction.dist)
                            }
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
                        color = palette.color
                        opacity = number(0.5)
                        textAlign = TextAlign.right
                        alignSelf = AlignSelf.baseline
                        width = 100.pct
                        padding = Padding(0.px, 0.px, 2.px)
                    }
                    if (questionState == QuestionState.RESOLVED) {
                        +"correct answer"
                    } else {
                        if (prediction != null) {
                            if (props.groupPred != null) {
                                +"group $predictionTerm"
                            } else {
                                +"your $predictionTerm from $predictionAgoText"
                            }
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
                        } else if (questionState == QuestionState.CLOSED) {
                            +"you did not answer"
                        }
                    }
                }
            }
        }
    }
}
