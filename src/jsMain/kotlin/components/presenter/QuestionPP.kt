package components.presenter

import csstype.*
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.Box
import mui.system.responsive
import mui.system.sx
import react.FC
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
        sx {
            gap = 30.pt
            width = 100.pct
            height = 100.pct
            padding = themed(5)
        }

        Stack {
            sx {
                justifyContent = JustifyContent.center
                alignItems = AlignItems.center
                gap = 30.pt
                flexGrow = number(1.0)
            }
            Typography {
                variant = TypographyVariant.h2
                +question.name
            }

            Typography {
                variant = TypographyVariant.h4
                +question.description
            }
        }

        Stack {
            direction = responsive(StackDirection.row)
            sx {
                justifyContent = JustifyContent.spaceBetween
            }
            Typography {
                variant = TypographyVariant.h6
                +"${question.numPredictors} people answered"
            }
            Typography {
                variant = TypographyVariant.h6
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