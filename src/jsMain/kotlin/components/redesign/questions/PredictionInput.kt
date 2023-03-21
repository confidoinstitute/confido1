package components.redesign.questions

import components.redesign.forms.*
import react.*
import tools.confido.distributions.*
import tools.confido.spaces.*

external interface PredictionInputProps : Props {
    var space: Space
    var dist: ProbabilityDistribution?
    var onChange: ((ProbabilityDistribution) -> Unit)?
    var onCommit: ((ProbabilityDistribution) -> Unit)?
    var disabled: Boolean?
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