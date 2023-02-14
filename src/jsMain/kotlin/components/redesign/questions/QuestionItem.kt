package components.redesign.questions

import csstype.*
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span

enum class QuestionState {
    OPEN,
    CLOSED,
    RESOLVED,
    ANNULLED,
}

external interface QuestionItemProps : Props {
    // TODO: Replace with Question
    var questionName: String
    var questionState: QuestionState
    var answerCount: Int
    var estimateText: String
    var estimateDaysAgo: Int
}

private data class QuestionItemColors(
    val questionColor: Color,
    val secondaryColor: Color,
    val mutedColor: Color,
)

val QuestionItem = FC<QuestionItemProps> { props ->
    val colors = when (props.questionState) {
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

    div {
        css {
            display = Display.flex;
            flexDirection = FlexDirection.column
            alignItems = AlignItems.flexStart
            padding = Padding(9.px, 16.px)

            background = Color("#FFFFFF")
            borderRadius = 10.px
        }

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
                +props.questionState.toString()
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
                +props.questionName
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
                    +"${props.answerCount} answers"
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
                    // TODO: group estimate or empty (br)
                    +props.estimateText
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
                    // TODO: Text depends on value frame
                    //       - "your estimate from N days ago" (N minutes ago etc.)
                    //                ^ this can also be answer
                    //       - "answer to see the group estimate"
                    //       - "group estimate"
                    //       - "be the first to answer!"
                    +"your answer from ${props.estimateDaysAgo} days ago"
                }
            }
        }
    }
}
