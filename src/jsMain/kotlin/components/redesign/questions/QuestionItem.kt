package components.redesign.questions

import components.*
import components.redesign.basic.*
import csstype.*
import emotion.react.*
import hooks.*
import kotlinx.datetime.Clock
import react.*
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.router.dom.*
import tools.confido.distributions.*
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.utils.*

external interface QuestionItemProps : Props {
    var question: Question
    var groupPred: Prediction?
    var href: String?
}

external interface StatusChipProps : Props {
    var text: String
    var color: Color
}

val QuestionItem = FC<QuestionItemProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val prediction = appState.myPredictions[props.question.ref]

    val questionState = props.question.state

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

    // In case there is no "Open" change, this may be a question that predates question history tracking.
    val isNew = props.question.state == QuestionState.OPEN
            && props.question.stateHistory.firstOrNull { it.newState == QuestionState.OPEN }
        ?.let { firstOpen ->
            val age = Clock.System.now() - firstOpen.at
            // We want the "new" label to be visible for the typical question after a weekend,
            // so 5 days is a fairly reasonable cutoff.
            // We may change this in the future if this does not work very well.
            //
            // New state is also currently not removed when opening the question,
            // as of writing this, that is not tracked, so we remove it only when making a prediction.
            age.inWholeDays < 5 && prediction == null
        } ?: false

    Link {
        css(LinkUnstyled) {
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.flexStart
            padding = Padding(9.px, 16.px)

            background = Color("#FFFFFF")
            borderRadius = 10.px

            hover {
                boxShadow = BoxShadow(0.px, 0.px, 5.px, Color("#CCCCCC"))
            }
        }

        props.href?.let {
            to = it
        }

        // Question Status Frame
        Stack {
            direction = FlexDirection.row
            css {
                padding = Padding(0.px, 0.px, 2.px)
                gap = 5.px
            }

            StatusChip {
                color = palette.color
                text = when (questionState) {
                    QuestionState.OPEN -> "Open"
                    QuestionState.CLOSED -> "Closed"
                    QuestionState.RESOLVED -> "Resolved"
                    QuestionState.ANNULLED -> "Annulled"
                }
            }

            if (!props.question.visible) {
                StatusChip {
                    color = Color("#ADBDC2")
                    text = "Hidden"
                }
            }
            if (isNew) {
                StatusChip {
                    color = Color("#FF9330")
                    text = "New"
                }
            }
        }

        // Question Frame
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.flexStart
                padding = Padding(8.px, 0.px)
            }
            span {
                css {
                    fontFamily = serif
                    fontWeight = integer(700)
                    fontSize = 30.px
                    lineHeight = 105.pct
                    color = Color("#000000")
                }
                +props.question.name
            }
        }

        // "Footer" Frame
        div {
            css {
                display = Display.flex
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
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    alignItems = AlignItems.baseline
                    flex = None.none
                    padding = 0.px
                    gap = 10.px
                }
                span {
                    css {
                        fontFamily = sansSerif
                        fontStyle = FontStyle.normal
                        fontWeight = integer(700)
                        fontSize = 14.px
                        lineHeight = 17.px
                    }

                    +pluralize(predictionTerm, props.question.numPredictors, includeCount = true)
                }
            }

            // Estimate Frame
            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    alignItems = AlignItems.flexStart

                    flexGrow = number(1.0)
                }

                // Value Frame
                div {
                    css {
                        fontFamily = sansSerif
                        fontStyle = FontStyle.normal
                        fontWeight = integer(800)
                        fontSize = 28.px
                        lineHeight = 33.px
                        color = palette.color
                        textAlign = TextAlign.right
                        width = 100.pct
                    }

                    fun renderDistribution(dist: ProbabilityDistribution) {
                        val space = dist.space
                        when (dist) {
                            is BinaryDistribution -> {
                                +"${(dist.yesProb * 100).toFixed(0)}%"
                            }

                            is ContinuousProbabilityDistribution -> {
                                val interval = dist.confidenceInterval(0.8, preferredCenter = dist.median)
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
                        fontFamily = sansSerif
                        fontStyle = FontStyle.normal
                        fontWeight = integer(700)
                        fontSize = 11.px
                        lineHeight = 13.px
                        color = palette.color
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
                                if (props.question.groupPredictionVisibility == GroupPredictionVisibility.ANSWERED) {
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

private val StatusChip = FC<StatusChipProps> { props ->
    span {
        css {
            fontFamily = sansSerif
            fontStyle = FontStyle.normal
            fontWeight = integer(600)
            fontSize = 11.px
            lineHeight = 13.px
            backgroundColor = props.color
            color = Color("#FFFFFF")
            padding = Padding(3.px, 7.px)
            borderRadius = 20.px
        }
        +props.text
    }
}

