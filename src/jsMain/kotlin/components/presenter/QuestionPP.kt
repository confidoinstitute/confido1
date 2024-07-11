package components.presenter

import components.redesign.basic.Stack
import csstype.*
import emotion.css.ClassName
import emotion.react.css
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.h6
import tools.confido.refs.deref
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.NumericSpace
import tools.confido.state.QuestionPV
import utils.byTheme
import utils.themed

val QuestionPP = FC<PresenterPageProps<QuestionPV>> { props ->
    val question = props.view.question.deref() ?: return@FC
    val space = question.answerSpace

    Stack {
        css {
            gap = 30.pt
            width = 100.pct
            height = 100.pct
            padding = 2.5.vh
        }

        Stack {
            css {
                justifyContent = JustifyContent.center
                alignItems = AlignItems.center
                gap = 30.pt
                flexGrow = number(1.0)
            }
            h1 {
                css {
                    fontSize = 7.vh
                    fontWeight = FontWeight.bold
                    textAlign = TextAlign.center
                    lineHeight = number(1.15)
                }
                +question.name
            }

            div {
                css {
                    lineHeight = number(1.15)
                    fontSize = 3.vh
                    textAlign = TextAlign.center
                }
                +question.description
            }
        }

        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.spaceBetween

                lineHeight = number(1.15)
                fontSize = 4.vh
            }
            div {
                +"${question.numPredictors} people answered"
            }
            div {
                css {
                    textAlign = TextAlign.right
                }
                if (question.open) {
                    +"Please answer "
                } else {
                    +"Expected answer is "
                }
                when (space) {
                    BinarySpace -> +"with yes or no"
                    is NumericSpace -> +"between ${space.formatValue(space.min)} and ${space.formatValue(space.max)}"
                }
            }
        }
    }
}