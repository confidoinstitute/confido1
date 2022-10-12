package components

import csstype.AlignItems
import csstype.Display
import csstype.JustifyContent
import csstype.px
import emotion.react.css
import mui.material.*
import mui.system.responsive
import mui.system.sx
import react.*
import space.kscience.plotly.layout
import space.kscience.plotly.models.Bar
import space.kscience.plotly.models.Pie
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.*
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.NumericSpace
import utils.jsObject
import kotlin.js.Date

external interface PredictionPlotProps : Props {
    var question: Question
    var distribution: ProbabilityDistribution?
}

val PredictionPlot = FC<PredictionPlotProps> {props ->
    val config = jsObject {
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