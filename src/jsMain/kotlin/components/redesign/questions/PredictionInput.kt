package components.redesign.questions

import components.redesign.forms.BinaryPredInput
import components.redesign.forms.BinaryPrediction
import react.FC
import react.Props
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.Space

external interface PredictionInputProps : Props {
    var space: Space
    var dist: ProbabilityDistribution?
    var onChange: ((ProbabilityDistribution) -> Unit)?
    var onCommit: ((ProbabilityDistribution) -> Unit)?
}

val PredictionInput = FC<PredictionInputProps> { props->
    val space = props.space
    when (space) {
        is NumericSpace -> NumericPredInput{
                +props
            }
        is BinarySpace -> BinaryPredInput {
            +props
        }
        else -> +"Not implemented"
    }
}

external interface PredictionGraphProps : Props {
    var space: Space
    var dist: ProbabilityDistribution?
}
val PredictionGraph = FC<PredictionGraphProps> {props->
    val space = props.space
    when (space) {
        is NumericSpace -> NumericPredGraph{
            this.space = space
            this.dist = props.dist as? ContinuousProbabilityDistribution?
            this.preferredCICenter = this.dist?.median
        }
        is BinarySpace -> BinaryPrediction {
            this.yesProb = (props.dist as? BinaryDistribution?)?.yesProb
        }
    }

}