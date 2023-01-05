package components

import csstype.AlignItems
import csstype.Display
import csstype.JustifyContent
import csstype.px
import kotlinx.js.jso
import mui.material.*
import mui.system.responsive
import mui.system.sx
import react.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.*

external interface PredictionPlotProps : Props {
    var question: Question
    var distribution: ProbabilityDistribution?
}

val PredictionPlot = FC<PredictionPlotProps> {props ->
    val config = jso<dynamic> {
        this.responsive = true
    }

    Card {
        raised = true


        CardHeader {
            title = ReactNode(props.question.name)
        }
        CardContent {
            sx {
                height = 500.px
                minWidth = 500.px
                if (props.distribution == null) {
                    display = Display.flex
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                }
            }
            props.distribution?.let {
                DistributionPlot {
                    this.distribution = it
                }
            } ?: run {
                Typography {
                    +"(no predictions)"
                }
            }
        }
    }
}

external interface GroupPredictionsProps : Props {
    var questions: List<Question>
}

val GroupPredictions = FC<GroupPredictionsProps> { props ->
    val questions = props.questions
    Grid {
        container = true
        spacing = responsive(2)
        columns = responsive(2)

        questions.map { question ->
            Grid {
                item = true
                xs = true
                PredictionPlot {
                    this.distribution = null
                    this.question = question
                    key = question.id
                }
            }
        }
    }
}

// Workaround for https://github.com/JetBrains/kotlin-wrappers/issues/1856
inline var GridProps.xs: Any?
    get() = asDynamic().xs
    set(value) {
        asDynamic().xs = value
    }