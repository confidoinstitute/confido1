package components.redesign.questions

import react.FC
import react.Props
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.Space

external interface PredictionInputProps : Props {
    var space: Space
    var dist: ProbabilityDistribution?
}

val PredictionInput = FC<PredictionInputProps> { props->
    val space = props.space
    when (space) {
        is NumericSpace -> NumericPredInput{
                this.space = space
                this.dist = props.dist as? ContinuousProbabilityDistribution?
            }
        else -> +"Not implemented"
    }
}